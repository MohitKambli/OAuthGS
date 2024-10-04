import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import Dashboard from './components/Dashboard'; // Import your Dashboard component
import Home from './components/Home'; // Make sure to import Home component

const App = () => {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<Home />} /> {/* Use 'element' prop here */}
        <Route path="/dashboard" element={<Dashboard />} /> {/* Use 'element' prop here */}
      </Routes>
    </Router>
  );
};

export default App;
