import React from 'react';
import {mount} from "enzyme";
import {Provider} from "react-redux";
import configureStore from 'redux-mock-store';
import thunk from 'redux-thunk';
import promiseMiddleware from "redux-promise-middleware";
import ConnectedMetadata from "./Metadata";
import Vocabulary from "../../services/MetadataAPI/Vocabulary";
import MetadataViewer from "./MetadataViewer";
import Config from "../../services/Config/Config";
import {
    CLASS_URI, DOMAIN_URI, LABEL_URI, PROPERTY_URI
} from "../../services/MetadataAPI/MetadataAPI";

const middlewares = [thunk, promiseMiddleware()];
const mockStore = configureStore(middlewares);

beforeAll(() => {
    window.fetch = jest.fn(() => Promise.resolve({ok: true, json: () => ({})}));

    Config.setConfig({
        urls: {
            metadata: "/metadata"
        }
    });

    return Config.init();
});

const vocabulary = [
    {
        "@id": "@type",
        '@type': PROPERTY_URI,
        [LABEL_URI]: [{'@value': 'Type'}],
        [DOMAIN_URI]: [
            {"@id": "http://fairspace.io/ontology#Collection"}
        ]
    },
    {
        '@id': 'http://www.w3.org/2000/01/rdf-schema#label',
        '@type': PROPERTY_URI,
        [LABEL_URI]: [{'@value': 'Name'}],
        [DOMAIN_URI]: [{'@id': 'http://fairspace.io/ontology#Collection'}]
    },
    {
        '@id': 'http://fairspace.io/ontology#description',
        '@type': PROPERTY_URI,
        [LABEL_URI]: [{'@value': 'Description'}],
        [DOMAIN_URI]: [{'@id': 'http://fairspace.io/ontology#Collection'}]
    },
    {
        '@id': 'http://schema.org/Creator',
        '@type': PROPERTY_URI,
        [LABEL_URI]: [{'@value': 'Creator'}],
        [DOMAIN_URI]: []
    },
    {
        '@id': 'http://schema.org/CreatedDate',
        '@type': PROPERTY_URI,
        [LABEL_URI]: [{'@value': 'Created date'}],
        [DOMAIN_URI]: [{'@id': 'http://fairspace.io/ontology#Collection'}]
    },
    {
        '@id': 'http://fairspace.io/ontology#Collection',
        '@type': CLASS_URI,
        [LABEL_URI]: [{'@value': 'Collection'}]
    }
];

it('shows result when subject provided and data is loaded', () => {
    const store = mockStore({
        metadataBySubject: {
            "http://fairspace.com/iri/collections/1": {
                data: [
                    {key: 'test', values: []}
                ]
            }
        },
        cache: {
            vocabulary:
            {
                data: new Vocabulary(vocabulary)
            }
        }
    });

    const collection = {
        uri: "http://fairspace.com/iri/collections/1"
    };

    const wrapper = mount(
        <Provider store={store}>
            <ConnectedMetadata editable subject={collection.uri} />
        </Provider>
    );

    expect(wrapper.find(MetadataViewer).length).toEqual(1);
});

it('shows a message if no metadata was found', () => {
    const store = mockStore({
        metadataBySubject: {
            "http://fairspace.com/iri/collections/1": {
                data: []
            }
        },
        cache: {
            vocabulary:
            {
                data: new Vocabulary(vocabulary)
            }
        }
    });

    const wrapper = mount(<ConnectedMetadata subject="http://fairspace.com/iri/collections/1" store={store} />);

    expect(wrapper.text()).toContain("(404) No such resource.");
});

it('shows error when no subject provided', () => {
    const store = mockStore({
        metadataBySubject: {},
        cache: {
            vocabulary:
            {
                data: new Vocabulary(vocabulary)
            }
        }
    });
    const wrapper = mount(<ConnectedMetadata subject={null} store={store} />);

    expect(wrapper.text()).toContain("An error occurred while loading metadata");
});

// TODO: review
// it('tries to load the metadata and the vocabulary', () => {
//     const store = mockStore({
//         cache: {
//             jsonLdBySubject: {
//                 "http://fairspace.com/iri/collections/1": {
//                     data: []
//                 }
//             },
//             vocabulary: {
//                 data: new Vocabulary(vocabulary)
//             }
//         }
//     });

//     const dispatch = jest.fn();
//     const wrapper = mount(<Metadata subject="John" store={store} dispatch={dispatch} />);
//     expect(dispatch.mock.calls.length).toEqual(1);
// });