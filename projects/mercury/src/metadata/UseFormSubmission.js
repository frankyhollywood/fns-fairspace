import {useState} from "react";
import useIsMounted from "react-is-mounted-hook";
import {ErrorDialog} from "@fairspace/shared-frontend";

import ValidationErrorsDisplay from './common/ValidationErrorsDisplay';
import {partitionErrors} from "../common/utils/linkeddata/metadataUtils";

const onFormSubmissionError = (e, id) => {
    if (e.details) {
        ErrorDialog.renderError(ValidationErrorsDisplay, partitionErrors(e.details, id), e.message);
    } else {
        ErrorDialog.showError(e, `Error saving entity.\n${e.message}`);
    }
};

const useFormSubmission = (submitFunc, subject) => {
    const [isUpdating, setUpdating] = useState(false);
    const isMounted = useIsMounted();

    const submitForm = () => {
        setUpdating(true);

        submitFunc()
            .catch(e => onFormSubmissionError(e, subject))
            .then(() => isMounted() && setUpdating(false));
    };

    return {isUpdating, submitForm};
};

export default useFormSubmission;