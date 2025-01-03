import React from "react";
import { useRouteMatch } from "react-router-dom";
import HRMSCard from "./components/hrmscard";
import InboxFilter from "./components/InboxFilter";
import ActionModal from "./components/Modal";
import Assignments from "./components/pageComponents/assignment";
import HRBanner from "./components/pageComponents/Banner";
import SelectDateofBirthEmployment from "./components/pageComponents/EmployeeDOB";
import SelectEmployeePhoneNumber from "./components/pageComponents/EmployeePhoneNumber";
import Jurisdictions from "./components/pageComponents/jurisdiction";
import SelectDateofEmployment from "./components/pageComponents/SelectDateofEmployment";
import SelectEmployeeEmailId from "./components/pageComponents/SelectEmailId";
import SelectEmployeeCorrespondenceAddress from "./components/pageComponents/SelectEmployeeCorrespondenceAddress";
import SelectEmployeeGender from "./components/pageComponents/SelectEmployeeGender";
import SelectEmployeeId from "./components/pageComponents/SelectEmployeeId";
import SelectEmployeeName from "./components/pageComponents/SelectEmployeeName";
import SelectEmployeeType from "./components/pageComponents/SelectEmployeeType";
import EmployeeApp from "./pages";
import CreateEmployee from "./pages/createEmployee";
import CreateEmployeeStepForm from "./pages/CreateEmployeeStepForm";
import EditEmployee from "./pages/EditEmployee/index";
import Details from "./pages/EmployeeDetails";
import Inbox from "./pages/Inbox";
import Response from "./pages/Response";
//
import SelectEmployeeGuardianName from "./components/pageComponents/SelectEmployeeGuardianName";
import SelectEmployeeGuardianRelationship from "./components/pageComponents/SelectEmployeeGuardianRelationship";
import SelectEmploymentStatus from "./components/pageComponents/SelectEmploymentStatus";
import HRMSEmployeewiseReport from "./pages/HRMSEmployeewiseReport";
import SelectULB from "./components/pageComponents/SelectULB";
import EmployeeDetails from "./components/pageComponents/employeeCreationSubForms/EmployeeDetails";
import AdministrativeDetails from "./components/pageComponents/employeeCreationSubForms/AdministrativeDetails";
import Summary from "./components/pageComponents/employeeCreationSubForms/Summary";
//
import getRootReducer from "./redux/reducers";

export const HRMSReducers = getRootReducer;

export const HRMSModule = ({ stateCode, userType, tenants }) => {
  const moduleCode = "HR";
  const language = Digit.StoreData.getCurrentLanguage();
  const { isLoading, data: store } = Digit.Services.useStore({ stateCode, moduleCode, language });
  console.log("Tenants: ", tenants);
  Digit.SessionStorage.set("HRMS_TENANTS", tenants);
  const { path, url } = useRouteMatch();
  if (!Digit.Utils.hrmsAccess()) {
    return null;
  }
  if (userType === "employee") {
    return <EmployeeApp path={path} url={url} />;
  } else return null;
};

const componentsToRegister = {
  HRMSCard,
  HRMSDetails: Details,
  SelectEmployeeEmailId,
  SelectEmployeeName,
  SelectEmployeeId,
  Jurisdictions,
  Assignments,
  ActionModal,
  HRBanner,
  SelectEmployeePhoneNumber,
  SelectDateofEmployment,
  SelectEmployeeType,
  SelectEmployeeCorrespondenceAddress,
  SelectEmployeeGender,
  SelectDateofBirthEmployment,
  HRMSModule,
  HRMSResponse: Response,
  HREditEmpolyee: EditEmployee,
  HRCreateEmployee: CreateEmployee,
  HRCreateEmployeeStepForm: CreateEmployeeStepForm,
  HRInbox: Inbox,
  HRMS_INBOX_FILTER: (props) => <InboxFilter {...props} />,
  SelectEmployeeGuardianName,
  SelectEmployeeGuardianRelationship,
  SelectEmploymentStatus,
  HRMSEmployeewiseReport,
  SelectULB,
  EmployeeDetails,
  AdministrativeDetails,
  Summary
};

export const initHRMSComponents = () => {
  console.log("In initHRMSComponents");
  Object.entries(componentsToRegister).forEach(([key, value]) => {
    Digit.ComponentRegistryService.setComponent(key, value);
  });
};
