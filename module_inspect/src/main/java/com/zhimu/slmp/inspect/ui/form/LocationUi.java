package com.zhimu.slmp.inspect.ui.form;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.inspect.model.request.InspectionSubmitRequest;
import com.zhimu.slmp.inspect.ui.common.SiteJudge;

/**
 * 登记页的位置行：状态（定位中 / 现场 / 非现场 / 无位置）、距离、文字，以及提交用的 LocationRequest。
 */
public final class LocationUi {

    @NonNull
    public final SiteJudge.State state;
    @Nullable
    public final Integer distanceM;
    @NonNull
    public final String text;
    @Nullable
    final Double latitude;
    @Nullable
    final Double longitude;
    @Nullable
    final Double accuracyM;
    /** OK / DENIED / FAILED */
    @NonNull
    final String clientStatus;

    LocationUi(@NonNull SiteJudge.State state, @Nullable Integer distanceM, @Nullable Double latitude,
               @Nullable Double longitude, @Nullable Double accuracyM, @NonNull String clientStatus) {
        this.state = state;
        this.distanceM = distanceM;
        this.text = SiteJudge.text(state, distanceM);
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyM = accuracyM;
        this.clientStatus = clientStatus;
    }

    @NonNull
    static LocationUi locating() {
        return new LocationUi(SiteJudge.State.LOCATING, null, null, null, null,
                InspectionSubmitRequest.LocationRequest.FAILED);
    }

    @NonNull
    static LocationUi none(@NonNull String clientStatus) {
        return new LocationUi(SiteJudge.State.NO_LOCATION, null, null, null, null, clientStatus);
    }

    /** 已记录的位置（修改时显示原记录，不重新定位） */
    @NonNull
    static LocationUi recorded(@Nullable String locationStatus, @Nullable Integer distanceM) {
        SiteJudge.State s = "ON_SITE".equals(locationStatus) ? SiteJudge.State.ON_SITE
                : "OFF_SITE".equals(locationStatus) ? SiteJudge.State.OFF_SITE : SiteJudge.State.NO_LOCATION;
        return new LocationUi(s, distanceM, null, null, null, InspectionSubmitRequest.LocationRequest.OK);
    }

    public boolean isLocating() {
        return state == SiteJudge.State.LOCATING;
    }

    public boolean isOffSite() {
        return state == SiteJudge.State.OFF_SITE;
    }

    public boolean isNoLocation() {
        return state == SiteJudge.State.NO_LOCATION;
    }

    @NonNull
    InspectionSubmitRequest.LocationRequest toRequest() {
        InspectionSubmitRequest.LocationRequest r = new InspectionSubmitRequest.LocationRequest();
        boolean ok = InspectionSubmitRequest.LocationRequest.OK.equals(clientStatus) && latitude != null;
        r.clientStatus = ok ? InspectionSubmitRequest.LocationRequest.OK
                : InspectionSubmitRequest.LocationRequest.OK.equals(clientStatus)
                ? InspectionSubmitRequest.LocationRequest.FAILED : clientStatus;
        r.latitude = ok ? latitude : null;
        r.longitude = ok ? longitude : null;
        r.accuracyM = ok ? accuracyM : null;
        return r;
    }
}
