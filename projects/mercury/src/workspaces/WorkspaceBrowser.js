// @flow
import React, {useContext, useState} from 'react';
import Button from "@material-ui/core/Button";
import {useHistory, withRouter} from "react-router-dom";
import WorkspaceList from './WorkspaceList';
import WorkspaceContext from './WorkspaceContext';
import type {Workspace} from './WorkspacesAPI';
import WorkspaceEditor from './WorkspaceEditor';
import {isAdmin} from "../users/userUtils";
import UserContext from "../users/UserContext";
import ErrorDialog from "../common/components/ErrorDialog";
import MessageDisplay from "../common/components/MessageDisplay";
import LoadingInlay from "../common/components/LoadingInlay";

const WorkspaceBrowser = () => {
    const history = useHistory();
    const {currentUser, currentUserError, currentUserLoading} = useContext(UserContext);
    const {workspaces,
        workspacesLoading,
        workspacesError,
        createWorkspace,
        refreshWorkspaces} = useContext(WorkspaceContext);

    const loading = currentUserLoading || workspacesLoading;
    const error = currentUserError || workspacesError;

    const [creatingWorkspace, setCreatingWorkspace] = useState(false);
    const [loadingCreatedWorkspace, setLoadingCreatedWorkspace] = useState(false);

    const openCreateWorkspaceDialog = () => setCreatingWorkspace(true);

    const handleSaveWorkspace = async (workspace: Workspace) => {
        setLoadingCreatedWorkspace(true);
        return createWorkspace(workspace)
            .then(() => refreshWorkspaces())
            .then(() => {
                setCreatingWorkspace(false);
                setLoadingCreatedWorkspace(false);
                history.push(`/workspaces/?iri=${encodeURI(workspace.iri)}`);
            })
            .catch(err => {
                setLoadingCreatedWorkspace(false);
                const message = err && err.message ? err.message : "An error occurred while creating a workspace";
                ErrorDialog.showError(err, message);
            });
    };

    const closeCreateWorkspaceDialog = () => setCreatingWorkspace(false);

    const renderWorkspaceList = () => (
    // workspaces.forEach((workspace: Workspace) => {
    //     workspace.creatorObj = users.find(u => u.iri === workspace.createdBy);
    // });
        <>
            <WorkspaceList
                workspaces={workspaces}
            />
            {creatingWorkspace ? (
                <WorkspaceEditor
                    title="Create workspace"
                    onSubmit={handleSaveWorkspace}
                    onClose={closeCreateWorkspaceDialog}
                    creating={loadingCreatedWorkspace}
                    workspaces={workspaces}
                />
            ) : null}
        </>
    );

    const renderAddWorkspaceButton = () => (
        <Button
            style={{marginTop: 8}}
            color="primary"
            variant="contained"
            aria-label="Add"
            title="Create a new workspace"
            onClick={openCreateWorkspaceDialog}
        >
            New
        </Button>
    );

    if (error) {
        return <MessageDisplay message="An error occurred while loading workspaces" />;
    }

    return (
        <>
            {loading ? <LoadingInlay /> : renderWorkspaceList()}
            {isAdmin(currentUser) ? renderAddWorkspaceButton() : null }
        </>
    );
};

export default withRouter(WorkspaceBrowser);
