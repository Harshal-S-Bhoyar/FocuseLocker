fetch('http://127.0.0.1:3000/api/confirm-contact', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({ email: 'bhoyarharsh15@gmail.com', otp: '645149' })
})
.then(res => res.json())
.then(data => console.log('Response:', data))
.catch(err => console.error('Error:', err));
