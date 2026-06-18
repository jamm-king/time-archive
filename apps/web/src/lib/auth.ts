export type CurrentUser = {
  userId: string;
  email: string;
  displayName: string;
  createdAt: string;
};

export type AuthMode = "login" | "register";

export type AuthFormValues = {
  email: string;
  password: string;
  displayName?: string;
};

type CsrfTokenResponse = {
  token: string;
};

export async function fetchCurrentUser(signal?: AbortSignal): Promise<CurrentUser | null> {
  const response = await fetch("/api/me", {
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
    },
    signal,
  });

  if (response.status === 401) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`Current user request failed with HTTP ${response.status}`);
  }

  return parseCurrentUser(await response.json());
}

export async function authenticate(
  mode: AuthMode,
  values: AuthFormValues,
): Promise<CurrentUser> {
  const csrfToken = await fetchCsrfToken();
  const response = await fetch(`/api/auth/${mode}`, {
    method: "POST",
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": csrfToken,
    },
    body: JSON.stringify(
      mode === "register"
        ? {
            email: values.email,
            password: values.password,
            displayName: values.displayName,
          }
        : {
            email: values.email,
            password: values.password,
          },
    ),
  });

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  return parseCurrentUser(await response.json());
}

export async function logout(): Promise<void> {
  const csrfToken = await fetchCsrfToken();
  const response = await fetch("/api/auth/logout", {
    method: "POST",
    credentials: "same-origin",
    headers: {
      "X-XSRF-TOKEN": csrfToken,
    },
  });

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }
}

export async function fetchCsrfToken(): Promise<string> {
  const response = await fetch("/api/csrf", {
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(`CSRF token request failed with HTTP ${response.status}`);
  }

  const body = (await response.json()) as CsrfTokenResponse;
  if (typeof body.token !== "string" || body.token.length === 0) {
    throw new Error("CSRF token response has an invalid shape");
  }

  return body.token;
}

async function getErrorMessage(response: Response): Promise<string> {
  try {
    const body = await response.json();
    if (isRecord(body) && typeof body.message === "string") {
      return body.message;
    }
  } catch {
    return `Request failed with HTTP ${response.status}`;
  }

  return `Request failed with HTTP ${response.status}`;
}

function parseCurrentUser(value: unknown): CurrentUser {
  if (!isRecord(value)) {
    throw new Error("Current user response must be an object");
  }

  const { userId, email, displayName, createdAt } = value;
  if (
    typeof userId !== "string" ||
    typeof email !== "string" ||
    typeof displayName !== "string" ||
    typeof createdAt !== "string"
  ) {
    throw new Error("Current user response has an invalid shape");
  }

  return {
    userId,
    email,
    displayName,
    createdAt,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
