import React from 'react';

import Table from "@material-ui/core/Table";
import TableHead from "@material-ui/core/TableHead";
import TableRow from "@material-ui/core/TableRow";
import TableCell from "@material-ui/core/TableCell";
import TableBody from "@material-ui/core/TableBody";
import Icon from "@material-ui/core/Icon";
import ClickHandler from "../ClickHandler/ClickHandler";
import ButtonWithVerification from "../buttons/ButtonWithVerification/ButtonWithVerification";
import RenameBox from "mdi-material-ui/RenameBox";
import RenameButton from "../buttons/RenameButton/RenameButton";
import {Row} from "simple-flexbox";

function FileList(props) {
    if (!props.files || props.files.length === 0 || props.files[0] === null) {
        return "No files";
    } else {
        const selectedFilenames = props.selectedPath ? props.selectedPath.map(path => path.filename) : [];
        return (<Table>
                <TableHead>
                    <TableRow>
                        <TableCell></TableCell>
                        <TableCell>Name</TableCell>
                        <TableCell numeric>size</TableCell>
                        <TableCell numeric>Last Modified</TableCell>
                        <TableCell></TableCell>
                    </TableRow>
                </TableHead>
                <TableBody>
                    {props.files.map(row => {
                        return (
                            <ClickHandler
                                component={TableRow}
                                key={row.filename}
                                selected={selectedFilenames.indexOf(row.filename) > -1}
                                onSingleClick={() => props.onPathClick(row)}
                                onDoubleClick={() => props.onPathDoubleClick(row)}>
                                <TableCell>
                                    <Icon>{row.type === 'directory' ? 'folder_open' : 'note_open'}</Icon>
                                </TableCell>
                                <TableCell component="th" scope="row">
                                    {row.basename}
                                </TableCell>
                                <TableCell numeric>
                                    {row.size ? row.size : ''}
                                </TableCell>
                                <TableCell numeric>{row.lastmod}</TableCell>
                                <TableCell numeric>
                                    <Row>
                                        {props.onRename?
                                            <RenameButton currentName={row.basename} aria-label={"Rename " + row.basename} onRename={(newName) => props.onRename(row, newName)}>
                                                <RenameBox />
                                            </RenameButton> : null}
                                        {props.onDelete ?
                                            <ButtonWithVerification aria-label={"Delete " + row.basename} onClick={() => props.onDelete(row)}>
                                                <Icon>delete</Icon>
                                            </ButtonWithVerification> : null}
                                    </Row>
                                </TableCell>
                            </ClickHandler>
                        );
                    })}
                </TableBody>
            </Table>)
    }
}

export default FileList;
