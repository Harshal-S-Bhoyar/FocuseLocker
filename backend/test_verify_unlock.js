fetch('http://127.0.0.1:3000/api/verify-unlock-otp', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({ trustedContactEmail: 'bhoyarharsh15@gmail.com', otp: '198614' })
})
.then(res => res.json())
.then(data => console.log('Response:', data))
.catch(err => console.error('Error:', err));
