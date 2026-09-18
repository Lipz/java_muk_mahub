export type RegisterPayload = {
  name: string;
  email: string;
  password: string;
};

export type LoginPayload = {
  email: string;
  password: string;
};

/** Response of POST /api/auth/register */
export type UserResponse = {
  id: number;
  name: string;
  email: string;
  status: string;
  isAdmin: boolean;
};

/** Response of POST /api/auth/login */
export type LoginResponse = {
  accessToken: string;
  expiresInMs: number;
};

/** Shape decoded out of the JWT payload issued by the Spring API. */
export type JwtClaims = {
  iss?: string;
  /** subject — the user's email */
  sub: string;
  /** user id */
  uid: number;
  iat: number;
  exp: number;
};

export type ActionState = {
  error?: string;
  fieldErrors?: Record<string, string>;
  /**
   * Submitted values echoed back. React 19 resets a form once its action
   * settles, so without this the user would retype everything after an error.
   * Passwords are deliberately never echoed.
   */
  values?: { name?: string; email?: string };
};