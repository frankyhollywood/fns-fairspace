import React from 'react';
import {Table, TableBody, TableCell, TableHead, TableRow} from '@material-ui/core';
import {makeStyles} from '@material-ui/core/styles';
import type {MetadataViewColumn, MetadataViewData} from "./MetadataViewAPI";
import type {MetadataViewEntityWithLinkedFiles} from "./metadataViewUtils";
import {getContextualFileLink} from "./metadataViewUtils";
import {formatDate} from "../../common/utils/genericUtils";
import type {Collection} from "../../collections/CollectionAPI";
import {collectionAccessIcon} from "../../collections/collectionUtils";

type MetadataViewTableProperties = {
    data: MetadataViewData;
    columns: MetadataViewColumn[];
    visibleColumnNames: string[];
    idColumn: MetadataViewColumn;
    toggleRow: () => {};
    history: any;
    selected?: MetadataViewEntityWithLinkedFiles;
    collections: Collection[];
};

const useStyles = makeStyles(() => ({
    cellContents: {
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
    }
}));

export const MetadataViewTable = (props: MetadataViewTableProperties) => {
    const {columns, visibleColumnNames, data, toggleRow, selected, isResourcesView, idColumn, history, collections} = props;
    const classes = useStyles();
    const visibleColumns = columns.filter(column => visibleColumnNames.includes(column.name));
    const dataLinkColumn = columns.find(c => c.type === 'dataLink');
    const {rows} = data;

    const handleResultSingleClick = (itemIri, itemLabel, linkedFiles) => {
        if (selected && selected.iri === itemIri) {
            toggleRow();
        } else {
            toggleRow({label: itemLabel, iri: itemIri, linkedFiles: linkedFiles || []});
        }
    };

    const handleResultDoubleClick = (itemIri) => {
        if (isResourcesView) {
            history.push(getContextualFileLink(itemIri));
        }
    };

    const getAccess = (iri) => collections.find(c => c.iri === iri || iri.startsWith(c.iri + '/')).access;

    const renderTableCell = (row, column) => {
        if (isResourcesView && column.name === 'access') {
            const iri = row[idColumn.name][0].value;
            const access = getAccess(iri);
            return (
                <TableCell key={column.name}>
                    {collectionAccessIcon(access)}
                </TableCell>
            );
        }

        const value = row[column.name];
        const displayValue = (value || []).map(v => ((column.type === 'Date') ? formatDate(v.value) : v.label)).join(', ');

        return (
            <TableCell key={column.name}>
                <span className={classes.cellContents}>{displayValue}</span>
            </TableCell>
        );
    };

    return (
        <Table data-testid="results-table" size="small" stickyHeader>
            <TableHead>
                <TableRow>
                    {visibleColumns.map(column => (
                        <TableCell key={column.name} className={classes.cellContents}>{column.title}</TableCell>
                    ))}
                </TableRow>
            </TableHead>
            <TableBody>
                {idColumn && rows.map(row => (
                    <TableRow
                        className={classes.row}
                        key={row[idColumn.name][0].value}
                        hover={isResourcesView}
                        selected={selected && selected.iri === row[idColumn.name][0].value}
                        onClick={() => handleResultSingleClick(
                            row[idColumn.name][0].value,
                            row[idColumn.name][0].label,
                            dataLinkColumn ? row[dataLinkColumn.name] : []
                        )}
                        onDoubleClick={() => handleResultDoubleClick(row[idColumn.name][0].value)}
                    >
                        {visibleColumns.map(column => renderTableCell(row, column))}
                    </TableRow>
                ))}
            </TableBody>
        </Table>
    );
};

export default MetadataViewTable;
