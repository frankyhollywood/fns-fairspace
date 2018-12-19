import React from 'react';
import WithRightDrawer from "../../components/generic/WithRightDrawer/WithRightDrawer";
import RecentActivity from "../../components/generic/RecentActivity/RecentActivity";
import asPage from "../../containers/asPage/asPage";
import BreadCrumbs from "../../components/generic/BreadCrumbs/BreadCrumbs";
import Config from "../../services/Config/Config";

function Home(props) {
    return Config.get().enableExperimentalFeatures
        ? (
            <WithRightDrawer
                collapsible={false}
                mainContents={(
                    <React.Fragment>
                        <BreadCrumbs />
                    </React.Fragment>
                )}
                drawerContents={<RecentActivity />}
            />
        )
        : (
            <React.Fragment>
                <BreadCrumbs />
            </React.Fragment>
        );
}

export default asPage(Home);
