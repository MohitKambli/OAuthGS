import React, { useEffect, useState } from 'react';

const Dashboard = () => {
  const [sheets, setSheets] = useState([]); // Store list of spreadsheets
  const [loading, setLoading] = useState(false); // Loading state for fetching sheets
  const [error, setError] = useState(null); // Error state
  const [accessToken, setAccessToken] = useState(null);
  const [selectedSheetData, setSelectedSheetData] = useState(null); // Data for the selected spreadsheet
  const [loadingSheetData, setLoadingSheetData] = useState(false); // Loading state for specific sheet data

  // Pagination states
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 10; // Number of sheets per page

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

  // Calculate the total number of pages
  const totalPages = Math.ceil(sheets.length / itemsPerPage);

  // Calculate the sheets to be displayed on the current page
  const indexOfLastItem = currentPage * itemsPerPage;
  const indexOfFirstItem = indexOfLastItem - itemsPerPage;
  const currentSheets = sheets.slice(indexOfFirstItem, indexOfLastItem);

  // Function to handle page changes
  const handlePageChange = (newPage) => {
    if (newPage > 0 && newPage <= totalPages) {
      setCurrentPage(newPage);
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
        <>
          {currentSheets.map((sheet) => (
            <div
              key={sheet.id}
              style={{ cursor: 'pointer', marginBottom: '10px', padding: '10px', border: '1px solid black' }}
              onClick={() => handleSheetClick(sheet.id)} // Fetch data for the clicked spreadsheet
            >
              {sheet.name} (ID: {sheet.id}) {/* You can display spreadsheet name or any relevant info */}
            </div>
          ))}

          {/* Pagination controls */}
          <div style={{ marginTop: '20px' }}>
            <button
              onClick={() => handlePageChange(currentPage - 1)}
              disabled={currentPage === 1}
            >
              Previous
            </button>
            <span style={{ margin: '0 10px' }}>Page {currentPage} of {totalPages}</span>
            <button
              onClick={() => handlePageChange(currentPage + 1)}
              disabled={currentPage === totalPages}
            >
              Next
            </button>
          </div>
        </>
      ) : (
        <div>No spreadsheets found.</div>
      )}
    </div>
  );
};

export default Dashboard;
