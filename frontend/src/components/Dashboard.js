import React, { useEffect, useState } from 'react';

const Dashboard = () => {
  const [sheets, setSheets] = useState([]); // Store list of spreadsheets
  const [loading, setLoading] = useState(false); // Loading state for fetching sheets
  const [error, setError] = useState(null); // Error state
  const [accessToken, setAccessToken] = useState(null);
  const [selectedSheetData, setSelectedSheetData] = useState(null); // Data for the selected spreadsheet
  const [loadingSheetData, setLoadingSheetData] = useState(false); // Loading state for specific sheet data

  useEffect(() => {
    // Extract the accessToken from the URL
    const urlParams = new URLSearchParams(window.location.search);
    const token = urlParams.get('accessToken');
    if (token) {
      setAccessToken(token);
      localStorage.setItem('accessToken', token); // Optionally store in localStorage for persistence
    }
  }, []);

  // Fetch list of spreadsheets when the dashboard loads
  useEffect(() => {
    const fetchSheets = async () => {
      setLoading(true);
      setError(null);
      try {
        const token = localStorage.getItem('accessToken') || accessToken; // Use stored token or state token
        if (!token) {
          throw new Error('No access token available');
        }
        const response = await fetch(`http://localhost:8080/spreadsheets/${token}`, {
          headers: {
            Authorization: `Bearer ${token}`, // Include access token in headers
          },
        });
        if (!response.ok) {
          throw new Error('Failed to fetch spreadsheets');
        }
        const result = await response.json();
        setSheets(result.spreadsheets);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    if (accessToken) {
      fetchSheets();
    }
  }, [accessToken]);

  // Function to fetch data for a specific sheet when clicked
  const handleSheetClick = async (spreadsheetId) => {
    setLoading(true);
    setError(null);
    try {
      const token = localStorage.getItem('accessToken') || accessToken;
      if (!token) {
        throw new Error('No access token available');
      }
      const response = await fetch(`http://localhost:8080/spreadsheet/${token}/${spreadsheetId}/data`, {
        headers: {
          Authorization: `Bearer ${token}`, // Include access token in headers
        },
      });
      if (!response.ok) {
        throw new Error('Failed to fetch spreadsheet data');
      }
      const result = await response.text();
      setSelectedSheetData(result);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return <div>Loading spreadsheets...</div>;
  }

  if (error) {
    return <div>Error: {error}</div>;
  }

  return (
    <div>
      <h1>Available Spreadsheets</h1>
      {sheets.length > 0 ? (
        sheets.map((sheet) => (
          <div
            key={sheet.id}
            style={{ cursor: 'pointer', marginBottom: '10px', padding: '10px', border: '1px solid black' }}
            onClick={() => handleSheetClick(sheet.id)} // Fetch data for the clicked spreadsheet
          >
            {sheet.name} (ID: {sheet.id}) {/* You can display spreadsheet name or any relevant info */}
          </div>
        ))
      ) : (
        <div>No spreadsheets found.</div>
      )}
    </div>
  );
};

export default Dashboard;
