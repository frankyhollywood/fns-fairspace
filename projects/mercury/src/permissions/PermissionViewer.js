import React, {useContext} from 'react';
import PropTypes from "prop-types";
import MessageDisplay from "../common/components/MessageDisplay";
import LoadingInlay from "../common/components/LoadingInlay";
import UserPermissionsComponent from "./UserPermissionsComponent";
import UserContext from "../users/UserContext";
import CollectionsContext from "../collections/CollectionsContext";
import WorkspacePermissionsComponent from "./WorkspacePermissionsComponent";
import {sortPermissions} from "../collections/collectionUtils";

export const PermissionViewer = ({collection, workspaceUsers, collaboratingWorkspaces,
    collaboratingUsers, currentUser, setPermission, error, loading}) => {
    if (error) {
        return (<MessageDisplay message="An error occurred loading permissions" />);
    }
    if (loading) {
        return (<LoadingInlay />);
    }

    const renderUserPermissionComponent = () => (
        <UserPermissionsComponent
            permissions={sortPermissions(collaboratingUsers)}
            collection={collection}
            setPermission={setPermission}
            currentUser={currentUser}
            workspaceUsers={workspaceUsers}
        />
    );

    const renderWorkspacePermissionComponent = () => (
        <WorkspacePermissionsComponent
            permissions={sortPermissions(collaboratingWorkspaces)}
            setPermission={setPermission}
            collection={collection}
        />
    );

    return (
        <div>
            {renderUserPermissionComponent()}
            {renderWorkspacePermissionComponent()}
        </div>
    );
};

PermissionViewer.defaultProps = {
    collection: PropTypes.object.isRequired,
    workspaceUsers: PropTypes.array.isRequired,
    collaboratingWorkspaces: PropTypes.array,
    collaboratingUsers: PropTypes.array,
    currentUser: PropTypes.object,
    setPermission: PropTypes.func
};

const ContextualPermissionViewer = ({collection, workspaceUsers, collaboratingUsers, collaboratingWorkspaces}) => {
    const {currentUser, currentUserLoading, currentUserError} = useContext(UserContext);
    const {setPermission, loading, error} = useContext(CollectionsContext);

    return (
        <PermissionViewer
            loading={currentUserLoading || loading}
            error={currentUserError || error}
            setPermission={setPermission}
            currentUser={currentUser}
            collaboratingUsers={collaboratingUsers}
            collaboratingWorkspaces={collaboratingWorkspaces}
            collection={collection}
            workspaceUsers={workspaceUsers}
        />
    );
};

export default ContextualPermissionViewer;