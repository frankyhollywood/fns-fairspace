import configureStore from 'redux-mock-store';
import thunk from 'redux-thunk';
import promiseMiddleware from "redux-promise-middleware";
import {fetchMetadataBySubjectIfNeeded} from "./metadataActions";
import Config from "../services/Config/Config";
import configFile from "../config";
import {mockResponse} from "../utils/testUtils";
import {FETCH_METADATA} from "./actionTypes";

const subject = 'my-subject';
const middlewares = [thunk, promiseMiddleware];
const mockStore = configureStore(middlewares);

beforeAll(() => {
    Config.setConfig(Object.assign(configFile, {
        externalConfigurationFiles: [],
    }));
    return Config.init();
});

describe('fetchLinkedData metadata', () => {
    it('should fetchLinkedData data if nothing is present', () => {
        const store = mockStore({});

        window.fetch = jest.fn(() => Promise.resolve(mockResponse(JSON.stringify([{name: 'collection1'}]))));

        return store.dispatch(fetchMetadataBySubjectIfNeeded(subject))
            .then(() => {
                const actions = store.getActions();
                expect(actions.length).toEqual(2);
                expect(actions[0].type).toEqual(`${FETCH_METADATA}_PENDING`);
                expect(actions[0].meta.subject).toEqual(subject);
                expect(actions[1].type).toEqual(`${FETCH_METADATA}_FULFILLED`);
                expect(actions[1].meta.subject).toEqual(subject);
            });
    });

    it('should not fetchLinkedData data if data present', () => {
        const store = mockStore({
            cache: {
                jsonLdBySubject: {
                    [subject]: {
                        data: ['some-data']
                    }
                }
            }
        });

        return store.dispatch(fetchMetadataBySubjectIfNeeded(subject))
            .then(() => {
                const actions = store.getActions();
                expect(actions.length).toEqual(0);
            });
    });

    it('should fetchLinkedData data if an the data was invalidated', () => {
        const store = mockStore({
            cache: {
                jsonLdBySubject: {
                    [subject]: {
                        invalidated: true,
                        data: ['some-data']
                    }
                }
            }
        });

        window.fetch = jest.fn(() => Promise.resolve(mockResponse(JSON.stringify([{name: 'collection1'}]))));

        return store.dispatch(fetchMetadataBySubjectIfNeeded(subject))
            .then(() => {
                const actions = store.getActions();
                expect(actions.length).toEqual(2);
                expect(actions[0].type).toEqual(`${FETCH_METADATA}_PENDING`);
                expect(actions[0].meta.subject).toEqual(subject);
                expect(actions[1].type).toEqual(`${FETCH_METADATA}_FULFILLED`);
                expect(actions[1].meta.subject).toEqual(subject);
            });
    });

    it('should not fetchLinkedData data if already fetching', () => {
        const store = mockStore({
            cache: {
                jsonLdBySubject: {
                    [subject]: {
                        pending: true,
                        invalidated: true
                    }
                }
            }
        });

        return store.dispatch(fetchMetadataBySubjectIfNeeded(subject))
            .then(() => {
                const actions = store.getActions();
                expect(actions.length).toEqual(0);
            });
    });
});
