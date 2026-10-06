import http from 'k6/http';
import { check } from 'k6';
import { baseUrls, headers, commonThresholds } from './lib/config.js';

export const options = {
  thresholds: commonThresholds,
  scenarios: {
    graphqlAdmission: {
      executor: 'constant-arrival-rate',
      rate: 35,
      timeUnit: '1s',
      duration: __ENV.DURATION || '2m',
      preAllocatedVUs: 20,
      maxVUs: 120,
    },
  },
};

export default function () {
  const response = http.post(
    `${baseUrls[__ITER % baseUrls.length]}/graphql`,
    JSON.stringify({ query: '{ __typename }' }),
    {
      headers: Object.assign({}, headers, { 'Content-Type': 'application/json' }),
      tags: { endpoint: 'bff.graphql.admission' },
    },
  );
  check(response, {
    'graphql admission response is successful': (value) =>
      value.status === 200 && value.body.includes('data'),
  });
}
