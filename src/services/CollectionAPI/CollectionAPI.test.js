import CollectionAPI from "./CollectionAPI";
import Config from "../Config/Config";
import mockResponse from "../../utils/mockResponse";

beforeAll(() => {
    Config.setConfig({
        "urls": {
            "collections": "/collections"
        }
    });

    return Config.init();
});

it('retrieves data for collections', () => {
    window.fetch = jest.fn(() =>
        Promise.resolve(mockResponse(200, 'OK', JSON.stringify([{'name': 'collection1'}]))))
    ;

    CollectionAPI.getCollections()
    expect(window.fetch.mock.calls[0][0]).toEqual("/collections");
});
