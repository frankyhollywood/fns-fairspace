import {buildSearchUrl} from "../search/searchUtils";
import {COMMENT_URI, LABEL_URI} from "../constants";
import {compareTo} from "../permissions/permissionUtils";
import type {Collection, CollectionPermissions} from "./CollectionAPI";

export const getCollectionAbsolutePath = (location) => `/collections/${location}`;

export const handleCollectionSearchRedirect = (history, value) => {
    const searchUrl = value ? buildSearchUrl(value) : '';
    history.push(`/collections${searchUrl}`);
};

export const mapCollectionPermissions: CollectionPermissions = (access) => ({
    canManage: compareTo(access, "Manage"),
    canWrite: compareTo(access, "Write"),
    canRead: compareTo(access, "Read"),
});

export const mapCollectionNameAndDescriptionToMetadata = (name, description) => ({
    [LABEL_URI]: [{value: name}],
    [COMMENT_URI]: [{value: description}]
});

export const mapFilePropertiesToCollection: Collection = (properties) => ({
    iri: properties.iri,
    name: properties.name,
    ownerWorkspace: properties.ownedBy,
    location: properties.basename,
    description: properties.comment,
    dateCreated: properties.creationdate,
    createdBy: properties.createdBy,
    dateModified: properties.lastmod,
    ...(properties.access && mapCollectionPermissions(properties.access)),
    sharedWith: (typeof properties.sharedWith === 'string') ? properties.sharedWith.split(',') : [],
    permissions: properties.permissions
        .split(',')
        .map(s => s.split(' '))
        .map(([user, access]) => ({user, access}))
});
