import React from 'react';
import callIcon from '../assets/call_icon.png'


const HelpSection = () => {
  return (
    <div className="help-section" >
      {/* <div className="image" ><img src={CallIcon} alt="Call Icon" /></div> */}
      <h2 className="help-section-header" >Need Help? We're Just a Click Away</h2>
      <div className="help-section-button-container" >
        <button className="help-section-button" >
          {/* <FaPhone className="help-section-icon"/> */}
          {/* <div clssName="image" ><img src="../Images/Call_Icon.png" alt="Call Icon" /></div> */}
          <div className="help-section-text-container">
            <div className="help-section-medium">
                <div className='help-section-icon'><img src="https://raw.githubusercontent.com/anujkit/msevaImages/refs/heads/main/call.png" alt='call' /></div>
                <div>
                  <p>Toll Free</p>
                  <p>1800 1800 0172</p>
                </div>
            </div>
          </div>
        </button>
        <button className="help-section-button">
          {/* <FaWhatsapp className="help-section-icon"/> */}
          <div className="help-section-text-container" >
            <span clssName="help-section-medium">Whatsapp</span>
            <span className="help-section-contact-no">897654509</span>
          </div>
        </button>
        <button className="help-section-button" >
          {/* <FaEnvelope className="help-section-icon" /> */}
          <div className="help-section-text-container">
            <span className="help-section-medium">Online Payment Issues</span>
            <span className="help-section-contact-no">egov123@gmail.com</span>
          </div>
        </button>
      </div>
    </div>
  );
};

export default HelpSection;