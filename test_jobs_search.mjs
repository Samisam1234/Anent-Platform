export default async function run(page, ui) {
  // First call the jobs search API
  const response = await page.evaluate(async () => {
    const res = await fetch('http://localhost:8080/api/v1/jobs/search', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({limit: 10})
    });
    return await res.json();
  });
  
  console.log('Jobs search response:', JSON.stringify(response, null, 2));
  
  return { success: true };
}