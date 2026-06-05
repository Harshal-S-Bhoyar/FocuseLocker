fetch('http://127.0.0.1:3000/api/request-unlock', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    trustedContactEmail: 'bhoyarharsh15@gmail.com',
    blockedCategories: ['Social Media', 'Games'],
    blockedKeywords: ['youtube', 'reddit'],
    timeRemaining: '02:30:00',
    requestTimestamp: Date.now()
  })
})
.then(res => res.json())
.then(data => console.log('Response:', data))
.catch(err => console.error('Error:', err));
