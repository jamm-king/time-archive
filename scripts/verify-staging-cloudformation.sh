#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-staging-cloudformation] %s\n' "$1"
}

fail() {
  printf '[verify-staging-cloudformation] ERROR: %s\n' "$1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE="$ROOT_DIR/infra/cloudformation/staging.yml"
PARAMETERS="$ROOT_DIR/infra/cloudformation/staging.parameters.example.json"

[[ -f "$TEMPLATE" ]] || fail "CloudFormation template not found: $TEMPLATE"
[[ -f "$PARAMETERS" ]] || fail "CloudFormation parameter example not found: $PARAMETERS"

if [[ -n "${CFN_LINT_BIN:-}" ]]; then
  [[ -x "$CFN_LINT_BIN" ]] || fail "CFN_LINT_BIN is not executable: $CFN_LINT_BIN"
  "$CFN_LINT_BIN" --regions ap-northeast-2 -- "$TEMPLATE"
else
  require_command cfn-lint
  cfn-lint --regions ap-northeast-2 -- "$TEMPLATE"
fi
log "cfn-lint schema validation passed"

if command -v python3 >/dev/null 2>&1 && python3 -c 'import yaml' >/dev/null 2>&1; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 && python -c 'import yaml' >/dev/null 2>&1; then
  PYTHON_BIN=python
else
  fail "python3 or python with PyYAML is required"
fi

"$PYTHON_BIN" - "$TEMPLATE" "$PARAMETERS" <<'PY'
import copy
import json
import re
import sys

import yaml


class CloudFormationLoader(yaml.SafeLoader):
    pass


TAG_NAMES = {
    "Equals": "Fn::Equals",
    "GetAtt": "Fn::GetAtt",
    "GetAZs": "Fn::GetAZs",
    "If": "Fn::If",
    "Join": "Fn::Join",
    "Not": "Fn::Not",
    "Ref": "Ref",
    "Select": "Fn::Select",
    "Sub": "Fn::Sub",
}


def construct_cloudformation_tag(loader, tag_suffix, node):
    key = TAG_NAMES.get(tag_suffix, f"Fn::{tag_suffix}")
    if isinstance(node, yaml.ScalarNode):
        value = loader.construct_scalar(node)
    elif isinstance(node, yaml.SequenceNode):
        value = loader.construct_sequence(node)
    else:
        value = loader.construct_mapping(node)
    return {key: value}


CloudFormationLoader.add_multi_constructor("!", construct_cloudformation_tag)


def ref(name):
    return {"Ref": name}


def sub_contains(value, expected):
    if not isinstance(value, dict) or "Fn::Sub" not in value:
        return False
    expression = value["Fn::Sub"]
    if isinstance(expression, list):
        expression = expression[0]
    return isinstance(expression, str) and expected in expression


def validate(template):
    errors = []
    resources = template.get("Resources", {})

    forbidden_types = {
        "AWS::CertificateManager::Certificate",
        "AWS::EC2::EIP",
        "AWS::EC2::NatGateway",
        "AWS::ElasticLoadBalancing::LoadBalancer",
        "AWS::ElasticLoadBalancingV2::Listener",
        "AWS::ElasticLoadBalancingV2::LoadBalancer",
        "AWS::ElasticLoadBalancingV2::TargetGroup",
    }
    found_forbidden = sorted(
        name
        for name, resource in resources.items()
        if resource.get("Type") in forbidden_types
    )
    if found_forbidden:
        errors.append(f"forbidden resources are present: {found_forbidden}")

    application_group = resources.get("ApplicationSecurityGroup", {}).get("Properties", {})
    if application_group.get("SecurityGroupIngress") not in (None, []):
        errors.append("application security group must not contain ingress rules")

    ingress_resources = [
        resource
        for resource in resources.values()
        if resource.get("Type") == "AWS::EC2::SecurityGroupIngress"
    ]
    if len(ingress_resources) != 1:
        errors.append("exactly one security group ingress resource is required")
    else:
        ingress = ingress_resources[0].get("Properties", {})
        expected_ingress = {
            "GroupId": ref("DatabaseSecurityGroup"),
            "SourceSecurityGroupId": ref("ApplicationSecurityGroup"),
            "IpProtocol": "tcp",
            "FromPort": 5432,
            "ToPort": 5432,
        }
        for key, expected in expected_ingress.items():
            if ingress.get(key) != expected:
                errors.append(f"database ingress has an invalid {key}")

    for name in ("DatabaseSubnetA", "DatabaseSubnetB"):
        properties = resources.get(name, {}).get("Properties", {})
        if properties.get("MapPublicIpOnLaunch") is not False:
            errors.append(f"{name} must not assign public IP addresses")

    application_subnet = resources.get("ApplicationSubnet", {}).get("Properties", {})
    if application_subnet.get("MapPublicIpOnLaunch") is not True:
        errors.append("application subnet must provide cost-conscious outbound public IPv4")

    instance = resources.get("ApplicationInstance", {}).get("Properties", {})
    creation_policy = resources.get("ApplicationInstance", {}).get("CreationPolicy", {})
    if creation_policy.get("ResourceSignal", {}).get("Count") != 1:
        errors.append("application instance must require one bootstrap success signal")
    if "KeyName" in instance:
        errors.append("application instance must not use an SSH key pair")
    metadata = instance.get("MetadataOptions", {})
    if metadata.get("HttpTokens") != "required":
        errors.append("application instance must require IMDSv2 tokens")
    interfaces = instance.get("NetworkInterfaces", [])
    if len(interfaces) != 1:
        errors.append("application instance must define exactly one network interface")
    else:
        interface = interfaces[0]
        if interface.get("GroupSet") != [ref("ApplicationSecurityGroup")]:
            errors.append("application instance must use only the application security group")
    mappings = instance.get("BlockDeviceMappings", [])
    if not mappings or mappings[0].get("Ebs", {}).get("Encrypted") is not True:
        errors.append("application root volume must be encrypted")
    user_data = instance.get("UserData", {}).get("Fn::Base64", {})
    if not sub_contains(user_data, "sha256sum --check --strict"):
        errors.append("host bootstrap must verify the Docker Compose binary checksum")
    if not sub_contains(user_data, "/opt/aws/bin/cfn-signal"):
        errors.append("host bootstrap must signal CloudFormation completion")

    database = resources.get("Database", {}).get("Properties", {})
    database_dependencies = resources.get("Database", {}).get("DependsOn", [])
    if "ApplicationInstance" not in database_dependencies:
        errors.append("database creation must wait for successful host bootstrap")
    required_database_values = {
        "PubliclyAccessible": False,
        "StorageEncrypted": True,
        "MultiAZ": False,
        "VPCSecurityGroups": [ref("DatabaseSecurityGroup")],
    }
    for key, expected in required_database_values.items():
        if database.get(key) != expected:
            errors.append(f"database has an invalid {key}")
    master_password = database.get("MasterUserPassword")
    if not sub_contains(master_password, "{{resolve:ssm-secure:${DatabaseMasterPasswordParameterName}}}"):
        errors.append("database master password must use the approved SSM SecureString reference")

    subnet_ids = (
        resources.get("DatabaseSubnetGroup", {})
        .get("Properties", {})
        .get("SubnetIds", [])
    )
    if subnet_ids != [ref("DatabaseSubnetA"), ref("DatabaseSubnetB")]:
        errors.append("database subnet group must use both private database subnets")

    for name in ("ApiRepository", "WebRepository"):
        repository = resources.get(name, {}).get("Properties", {})
        if repository.get("ImageTagMutability") != "IMMUTABLE":
            errors.append(f"{name} must use immutable image tags")
        if repository.get("ImageScanningConfiguration", {}).get("ScanOnPush") is not True:
            errors.append(f"{name} must scan images on push")

    application_policies = resources.get("ApplicationRole", {}).get("Properties", {}).get("Policies", [])
    parameter_resources = [
        statement.get("Resource")
        for policy in application_policies
        for statement in policy.get("PolicyDocument", {}).get("Statement", [])
        if "ssm:GetParametersByPath" in statement.get("Action", [])
    ]
    if len(parameter_resources) != 1 or not sub_contains(
        parameter_resources[0],
        "parameter/time-archive/${EnvironmentName}/*",
    ):
        errors.append("application role must be limited to the environment SSM path")

    publisher_trust = (
        resources.get("GitHubImagePublisherRole", {})
        .get("Properties", {})
        .get("AssumeRolePolicyDocument", {})
        .get("Statement", [{}])[0]
        .get("Condition", {})
        .get("StringEquals", {})
    )
    if not sub_contains(
        publisher_trust.get("token.actions.githubusercontent.com:sub"),
        "repo:${GitHubRepository}:ref:refs/heads/main",
    ):
        errors.append("GitHub image role trust must be limited to the main branch")

    deploy_trust = (
        resources.get("GitHubStagingDeployRole", {})
        .get("Properties", {})
        .get("AssumeRolePolicyDocument", {})
        .get("Statement", [{}])[0]
        .get("Condition", {})
        .get("StringEquals", {})
    )
    if not sub_contains(
        deploy_trust.get("token.actions.githubusercontent.com:sub"),
        "repo:${GitHubRepository}:environment:staging",
    ):
        errors.append("GitHub deploy role trust must be limited to the staging environment")

    return errors


with open(sys.argv[1], encoding="utf-8") as source:
    template = yaml.load(source, Loader=CloudFormationLoader)

errors = validate(template)
if errors:
    raise SystemExit("\n".join(f"- {error}" for error in errors))

with open(sys.argv[2], encoding="utf-8") as source:
    parameters = json.load(source)

if not isinstance(parameters, list):
    raise SystemExit("parameter example must be a CloudFormation parameter list")

parameter_keys = {item.get("ParameterKey") for item in parameters}
required_parameter_keys = {
    "AlertEmail",
    "DatabaseEngineVersion",
    "DockerComposeSha256",
    "DockerComposeVersion",
    "GitHubOidcProviderArn",
}
if parameter_keys != required_parameter_keys:
    raise SystemExit(f"unexpected parameter example keys: {sorted(parameter_keys)}")

raw_parameters = json.dumps(parameters)
if re.search(r"AKIA[0-9A-Z]{16}", raw_parameters):
    raise SystemExit("parameter example contains an AWS access key")
for forbidden_key in ("Password", "Secret", "TunnelToken", "AccessKey"):
    if forbidden_key in parameter_keys:
        raise SystemExit(f"parameter example contains a secret field: {forbidden_key}")

mutated = copy.deepcopy(template)
mutated["Resources"]["ApplicationSecurityGroup"]["Properties"]["SecurityGroupIngress"] = [
    {"IpProtocol": "tcp", "FromPort": 443, "ToPort": 443, "CidrIp": "0.0.0.0/0"}
]
if not validate(mutated):
    raise SystemExit("policy self-test failed to detect public application ingress")

mutated = copy.deepcopy(template)
mutated["Resources"]["Database"]["Properties"]["PubliclyAccessible"] = True
if not validate(mutated):
    raise SystemExit("policy self-test failed to detect public RDS")

print("staging CloudFormation architecture policy validation passed")
PY

log "Staging CloudFormation validation passed"
