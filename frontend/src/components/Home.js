import React from 'react';
const Home = () => {
    const handleLogin = () => {
      window.location.href = 'http://localhost:8080/auth/google';
    };
  
    return (
      <div>
        <h1>Login with Google</h1>
        <button onClick={handleLogin}>Login</button>
      </div>
    );
  };
  export default Home;