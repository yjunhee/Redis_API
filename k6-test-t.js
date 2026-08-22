import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    coupon_issue_scenario: {
      executor: 'shared-iterations',
      iterations: 500,
      vus: 500,
      maxDuration: '60s',
    },
  },
};

export default function () {
  const userId = `${__VU}-${__ITER}`;
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
