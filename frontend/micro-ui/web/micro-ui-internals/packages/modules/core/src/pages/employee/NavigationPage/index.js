import React, { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import { Loader, Toast } from "@upyog/digit-ui-react-components";
import { useTranslation } from "react-i18next";
import { serviceMappings } from "../../../config/ssoConfig";

const setEmployeeDetail = (userObject, token) => {
  let locale = JSON.parse(sessionStorage.getItem("Digit.locale"))?.value || "en_IN";
  localStorage.setItem("Employee.tenant-id", userObject?.tenantId);
  localStorage.setItem("tenant-id", userObject?.tenantId);
  localStorage.setItem("locale", locale);
  localStorage.setItem("Employee.locale", locale);
  localStorage.setItem("token", token);
  localStorage.setItem("Employee.token", token);
  localStorage.setItem("user-info", JSON.stringify(userObject));
  localStorage.setItem("Employee.user-info", JSON.stringify(userObject));
};

const NavigationApp = () => {
  const { t } = useTranslation();
  const location = useLocation();
  const [isLoading, setIsLoading] = useState(true);
  const [userName, setUserName] = useState("");
  const [tokenName, setTokenName] = useState("");
  const [serviceName, setServiceName] = useState("");
  const [navigateToUrl, setNavigateToUrl] = useState("");
  const [user, setUser] = useState(null);
  const [showToast, setShowToast] = useState(null);

  useEffect(() => {
    const queryParams = new URLSearchParams(location.search);
    setUserName(queryParams.get("userName"));
    setTokenName(queryParams.get("token"));
    setServiceName(queryParams.get("serviceName"));
  }, []);

  useEffect(() => {
    if (userName && tokenName) {
      callSsoAuthenticateUserApi();
    }
    setIsLoading(false);
  }, [userName, tokenName]);

  const callSsoAuthenticateUserApi = () => {
    const payload = {
      userName: userName,
      tokenName: tokenName,
    };
    Digit.HRMSService.ssoAuthenticateUser(payload)
      .then((response) => {
        if (response.status === true) {
          if (typeof response?.result?.url == "string") {
            setNavigateToUrl(response.result.url);
            callOauthTokenApi(response);
          } else {
            setIsLoading(false);
            setShowToast("Something went wrong");
            setTimeout(closeToast, 5000);
          }
        } else {
          setIsLoading(false);
          setShowToast(response.message);
          setTimeout(closeToast, 5000);
        }
      })
      .catch((err) => {
        setIsLoading(false);
        console.log("Error in Digit.HRMSService.ssoAuthenticateUser: ", err.response);
        setShowToast(err?.response?.data?.Errors?.[0]?.message || "Something went wrong");
        setTimeout(closeToast, 5000);
      });
  };

  const callOauthTokenApi = async (data) => {
    const payload = {
      username: userName,
      password: tokenName,
      tenantId: data.result.tenantId,
      userType: data.result.employeeType,
      thirdPartyName: "eSewa",
    };

    try {
      const { UserRequest: info, ...tokens } = await Digit.UserService.authenticate(payload);
      Digit.SessionStorage.set("Employee.tenantId", info?.tenantId);
      setUser({ info, ...tokens });
    } catch (err) {
      setIsLoading(false);
      console.log("Error in Digit.UserService.authenticate: ", err.response);
      setShowToast(err?.response?.data?.error_description || "Invalid login credentials");
      setTimeout(closeToast, 5000);
    }
  };

  useEffect(() => {
    if (user) {
      const filteredRoles = user?.info?.roles?.filter((role) => role.tenantId === Digit.SessionStorage.get("Employee.tenantId"));
      if (user?.info?.roles?.length > 0) user.info.roles = filteredRoles;
      Digit.UserService.setUser(user);
      setEmployeeDetail(user?.info, user?.access_token);
      handleServiceRedirection();
    } else {
      setIsLoading(false);
    }
  }, [user]);

  const handleServiceRedirection = () => {
    const servicePath = fetchServicePath(serviceName);
    setIsLoading(false);
    if (servicePath) {
      window.location.href = navigateToUrl + servicePath;
    } else {
      setShowToast("Service not found");
      setTimeout(closeToast, 5000);
    }
  };

  const fetchServicePath = (serviceName) => {
    const servicePath = serviceMappings.find((item) => item.serviceName === serviceName)?.path;
    return servicePath || "";
  };

  const closeToast = () => {
    setShowToast(null);
  };

  return (
    <div>
      {isLoading && <Loader />}
      {showToast && <Toast error={true} label={t(showToast)} onClose={closeToast} isDleteBtn={"true"} />}
    </div>
  );
};

export default NavigationApp;
