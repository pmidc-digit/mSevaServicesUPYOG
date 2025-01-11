import React, { useEffect, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
//
import { FormComposer } from "../../../../../../react-components/src/hoc/FormComposer";
import { updateEmployeeForm } from "../../../redux/actions/employeeFormActions";
import { Toast } from "@upyog/digit-ui-react-components";

const EmployeeDetails = ({ config, onGoNext, t }) => {
  const [canSubmit, setSubmitValve] = useState(false);
  function goNext(data) {
    //console.log(`Data in step ${config.currStepNumber} is: \n`, data);
    onGoNext();
  }

  const onFormValueChange = (setValue = true, data) => {
    console.log("Form Data: ", data);
    if (!_.isEqual(data, currentStepData)) {
      dispatch(updateEmployeeForm(config.key, data));
      checkConditions(data);
    }
  };

  const currentStepData = useSelector((state) => state.hrms.employeeForm.formData?.[config.key] ?? {});
  const dispatch = useDispatch();
  console.log("currentStepData in EmployeeDetails: ", currentStepData);

  const [mobileNumber, setMobileNumber] = useState(null);
  const [phonecheck, setPhonecheck] = useState(false);
  const [showToast, setShowToast] = useState(null);
  const tenantId = Digit.ULBService.getCurrentTenantId();
  useEffect(() => {
    if (mobileNumber && mobileNumber.length == 10 && mobileNumber.match(Digit.Utils.getPattern("MobileNo"))) {
      setShowToast(null);
      Digit.HRMSService.search(tenantId, null, { phone: mobileNumber }).then((result, err) => {
        if (result.Employees.length > 0) {
          setShowToast({ key: true, label: "ERR_HRMS_USER_EXIST_MOB" });
          setPhonecheck(false);
        } else {
          setPhonecheck(true);
        }
      });
    } else {
      setPhonecheck(false);
    }
  }, [mobileNumber]);
  const checkMailNameNum = (formData) => {
    const email = formData?.SelectEmployeeEmailId?.emailId || "";
    const name = formData?.SelectEmployeeName?.employeeName || "";
    const address = formData?.SelectEmployeeCorrespondenceAddress?.correspondenceAddress || "";
    const validEmail = email.length == 0 ? true : email.match(Digit.Utils.getPattern("Email"));
    return validEmail && name.match(Digit.Utils.getPattern("Name")) && address.match(Digit.Utils.getPattern("Address"));
  };
  const checkConditions = (formData) => {
    console.log("onFormValueChange: ", formData);
    if (formData?.SelectEmployeePhoneNumber?.mobileNumber) {
      setMobileNumber(formData?.SelectEmployeePhoneNumber?.mobileNumber);
    } else {
      setMobileNumber(formData?.SelectEmployeePhoneNumber?.mobileNumber);
    }
    if (
      formData?.SelectEmployeeName?.employeeName &&
      formData?.SelectEmployeePhoneNumber?.mobileNumber &&
      formData?.SelectEmployeeGender?.gender.code &&
      formData?.SelectEmployeeCorrespondenceAddress?.correspondenceAddress &&
      formData?.SelectDateofEmployment?.dateOfAppointment &&
      formData?.SelectEmployeeType?.code &&     
      phonecheck &&
      checkMailNameNum(formData)
    ) {
      setSubmitValve(true);
    } else {
      setSubmitValve(false);
    }
  };

  return (
    <React.Fragment>
      <FormComposer
        defaultValues={currentStepData}
        //heading={t("")}
        config={config.currStepConfig}
        onSubmit={goNext}
        onFormValueChange={onFormValueChange}
        isDisabled={!canSubmit}
        label={t(`${config.texts.submitBarLabel}`)}
      />
      {showToast && (
        <Toast
          error={showToast.key}
          label={t(showToast.label)}
          onClose={() => {
            setShowToast(null);
          }}
          isDleteBtn={"true"}
        />
      )}
    </React.Fragment>
  );
};

export default EmployeeDetails;
