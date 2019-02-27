import reducer from './searchReducers';
import * as actionTypes from "../actions/actionTypes";
import {testNoChangedOnUnknownActionType} from '../utils/testUtils';

testNoChangedOnUnknownActionType('Search reducers', reducer);

describe('Search reducers', () => {
    it('should return the initial state when no action is given', () => {
        const initialState = {
            pending: false,
            searchType: null,
            results: [],
            error: null,
        };
        expect(reducer(undefined, {}))
            .toEqual(initialState);
    });

    it('should return proper pending value after firing pending action', () => {
        expect(
            reducer(undefined, {
                type: actionTypes.PERFORM_SEARCH_PENDING,
                meta: {searchType: 'collections'},
            })
        ).toEqual({
            pending: true,
            searchType: 'collections',
            results: [],
            error: null,
        });
    });

    it('should update results on search success', () => {
        const results = ['item 1', 'item 2'];

        expect(
            reducer(undefined, {
                type: actionTypes.PERFORM_SEARCH_FULFILLED,
                payload: results,
                meta: {searchType: 'collections'},
            }).results
        ).toContain('item 1');
    });
});
