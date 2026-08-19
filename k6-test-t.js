import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    redis_failure_test: {
      executor: 'constant-arrival-rate',
      rate:  500,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 100,
      maxVUs: 1000,
    },
  },
};

export default function () {
  const userId = __VU;
  const couponId = 1;

  //const url =
  //  `http://localhost:8080/coupons/${couponId}/issue?userId=${userId}`;
  const url =
    `http://localhost:8080/coupons/${couponId}/issue/redis?userId=${userId}`;

    
  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    timeout: '60s',
  };

  const response = http.post(url, null, params);

  check(response, {
    'status is 200': (r) => r.status === 200,
    'status is 400': (r) => r.status === 400
  });
}
