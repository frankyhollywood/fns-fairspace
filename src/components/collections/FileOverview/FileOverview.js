import React from 'react';
import FileList from "../FileList/FileList";
import Error from "../../error/Error";

class FileOverview extends React.Component {
    constructor(props) {
        super(props);
        this.props = props;
        this.fileStore = props.fileStore;
        this.prefix = props.prefix;
        this.onFilesDidLoad = props.onFilesDidLoad;

        // Initialize state
        let path = props.path;

        this.state = {
            loading: false,
            path: path,
            contents: [],
            selectedPath: props.selectedPath
        };
    }

    componentDidMount() {
        this.loadContents(this.state.path);
    }

    loadContents(path) {
        this.setState({loading: true});
        this.fileStore.list(path)
            .then(json => this.setState({loading: false, contents: json}))
            .then(json => {
                if(this.onFilesDidLoad) {
                    this.onFilesDidLoad(json);
                }
            })
            .catch(err => {
                this.errorMessage = "Error loading files. ";
                console.error(this.errorMessage, err);
                this.setState({loading: false, error: true});
            })
    }

    componentWillReceiveProps(nextProps) {
        // See if we need updating
        if(nextProps.path !== this.props.path || nextProps.refreshFiles) {
            this.setState({
                path: nextProps.path,
                contents: [],
                selectedPath: nextProps.selectedPath
            });
            this.loadContents(nextProps.path);
        } else {
            this.setState({
                selectedPath: nextProps.selectedPath
            });
        }

        this.props = nextProps;
    }

    // Parse path into array
    getPath(path) {
        if(!path)
            return [];

        if(path[0] === '/')
            path = path.slice(1);

        return path ? path.split('/') : [];
    }

    render() {
        if (this.state.error) {
            return (<Error message={this.errorMessage}/>)
        }
        else if(this.state.loading) {
            return (<div>Loading...</div>);
        }

        return (<FileList
                    files={this.state.contents}
                    selectedPath={this.state.selectedPath}
                    onPathClick={this.props.onPathClick}
                    onPathDoubleClick={this.props.onPathDoubleClick} />);
    }
}

export default FileOverview;
