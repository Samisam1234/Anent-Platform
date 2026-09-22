export default async function run(page, ui) {
  // Call the jobs match API with the candidate ID
  const response = await page.evaluate(async () => {
    const res = await fetch('http://localhost:8080/api/v1/jobs/match', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({candidateProfileId: 7, limit: 10})
    });
    return await res.json();
  });
  
  console.log('Jobs match response:', JSON.stringify(response, null, 2));
  
  return { success: true };
}