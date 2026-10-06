export const baseUrl = __ENV.BASE_URL || 'http://localhost:28080';
export const accountsUrl = __ENV.ACCOUNTS_URL || 'http://localhost:28081';
export const expenseCoreUrl = __ENV.EXPENSE_CORE_URL || 'http://localhost:28082';
export const notificationsUrl = __ENV.NOTIFICATIONS_URL || 'http://localhost:28083';
if (!__ENV.BEARER_TOKEN) {
  throw new Error('BEARER_TOKEN must contain a signed token for k6 load tests');
}
export const token = __ENV.BEARER_TOKEN;
export const headers = { Authorization: `Bearer ${token}` };

export const commonThresholds = {
  http_req_failed: ['rate<0.01'],
  http_req_duration: ['p(95)<750', 'p(99)<1500'],
};
