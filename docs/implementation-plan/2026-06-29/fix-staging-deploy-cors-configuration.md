# Fix Staging Deploy CORS Configuration

## Objective

Fix the first staging deployment failure caused by Spring Security requiring a
`CorsConfigurationSource` bean when CORS support is enabled.

## Scope

- Add an explicit CORS configuration bean for the API security configuration.
- Keep the policy conservative and avoid broad cross-origin access.
- Add focused test coverage for the security configuration startup contract.
- Update this plan with verification results.

Out of scope:

- Changing public CORS policy for browser clients.
- Re-running the staging deployment from this branch.
- Modifying Cloudflare or edge routing.

## Relevant Files Or Modules

- `apps/api/src/main/kotlin/com/timearchive/configuration/SecurityConfiguration.kt`
- `apps/api/src/test/kotlin/com/timearchive/configuration/SecurityConfigurationTest.kt`

## Key Design Decisions

- Keep `.cors(Customizer.withDefaults())` in the security filter chain.
- Provide an explicit `UrlBasedCorsConfigurationSource` bean so Spring Security
  can start consistently in local, migration, and deployed profiles.
- Register an empty `CorsConfiguration` for API paths. This does not introduce
  permissive CORS origins; same-origin web-to-API proxying remains the intended
  path.

## Step-by-step Execution Plan

1. Inspect the staging SSM failure output.
2. Inspect the existing security configuration.
3. Add an explicit conservative CORS configuration source.
4. Add focused tests for bean creation and conservative defaults.
5. Run relevant backend tests.

## Risks And Rollback Strategy

- Risk: accidentally broadening cross-origin browser access. Mitigation: do not
  set allowed origins, methods, or headers in the default CORS configuration.
- Risk: changing CSRF/session behavior. Mitigation: keep the existing security
  filter chain behavior unchanged.
- Rollback: revert the CORS bean addition if a better environment-specific CORS
  policy is introduced later.

## Verification Plan

- Run focused security configuration tests.
- Run backend tests if feasible.
- Confirm no deployment secrets or local environment files are changed.

## Open Questions

- None.

## Progress

- Confirmed the failed SSM command reached Flyway migration and failed during
  Spring Security startup because no `CorsConfigurationSource` bean existed.
- Added an explicit conservative `CorsConfigurationSource` bean.
- Added focused test coverage for the CORS configuration source.

## Completion Summary

The staging deployment failed during the migration container startup after
Flyway successfully applied all migrations. Spring Security enabled CORS but no
`CorsConfigurationSource` bean existed, so application context initialization
failed.

The fix adds an explicit API-path CORS configuration source with no permissive
origins, methods, or headers. This satisfies Spring Security's startup contract
without changing the intended same-origin proxy deployment model.

## Files Changed

- `apps/api/src/main/kotlin/com/timearchive/configuration/SecurityConfiguration.kt`
- `apps/api/src/test/kotlin/com/timearchive/configuration/SecurityConfigurationTest.kt`

## Tests Run And Results

- `./gradlew.bat test --tests com.timearchive.configuration.SecurityConfigurationTest --max-workers=2`: passed.
- `./gradlew.bat test --max-workers=2`: passed.

## Manual Verification Results

No staging deployment rerun was performed from this branch. After merge, the
API and Web images must be republished for the new commit before rerunning the
staging deployment workflow.

## Known Limitations

- This fix does not define a broad public CORS policy. The deployed web app is
  still expected to access the API through the same-origin Web proxy.

## Follow-up Recommendations

- Merge the fix, publish staging images for the merged commit, then rerun the
  staging deployment workflow.
