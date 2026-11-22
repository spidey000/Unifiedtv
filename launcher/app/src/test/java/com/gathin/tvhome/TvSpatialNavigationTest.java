package com.gathin.tvhome;

import android.view.View;
import org.junit.Test;

import static org.junit.Assert.*;

/** Deterministic focus ranking without a device or emulator. */
public final class TvSpatialNavigationTest {
    private static final Class<?> SCORE = TvSpatialNavigation.SpatialScore.class;

    @Test public void prefersAlignedNeighbourOverDiagonal() {
        assertTrue(TvSpatialNavigation.SpatialScore.overlapsBeam(
                100, 100, 200, 200, 205, 120, 305, 220, View.FOCUS_RIGHT));
        assertFalse(TvSpatialNavigation.SpatialScore.overlapsBeam(
                100, 100, 200, 200, 205, 225, 305, 325, View.FOCUS_RIGHT));
    }

    @Test public void horizontalCandidateMustBeInRequestedDirection() {
        assertTrue(TvSpatialNavigation.SpatialScore.inDirection(
                100, 100, 200, 200, 215, 100, 315, 200, View.FOCUS_RIGHT));
        assertFalse(TvSpatialNavigation.SpatialScore.inDirection(
                100, 100, 200, 200, 0, 100, 95, 200, View.FOCUS_RIGHT));
    }

    @Test public void verticalCandidateMustBeInRequestedDirection() {
        assertTrue(TvSpatialNavigation.SpatialScore.inDirection(
                100, 100, 200, 200, 100, 210, 200, 310, View.FOCUS_DOWN));
        assertFalse(TvSpatialNavigation.SpatialScore.inDirection(
                100, 100, 200, 200, 100, 0, 200, 90, View.FOCUS_DOWN));
    }

    @Test public void closerPosterWinsAlongSameRow() {
        long near = TvSpatialNavigation.SpatialScore.score(
                100, 100, 200, 200, 210, 100, 310, 200, View.FOCUS_RIGHT);
        long far = TvSpatialNavigation.SpatialScore.score(
                100, 100, 200, 200, 370, 100, 470, 200, View.FOCUS_RIGHT);
        assertTrue(near < far);
    }

    @Test public void closerGridRowWins() {
        long near = TvSpatialNavigation.SpatialScore.score(
                100, 100, 200, 200, 100, 211, 200, 311, View.FOCUS_DOWN);
        long far = TvSpatialNavigation.SpatialScore.score(
                100, 100, 200, 200, 100, 550, 200, 650, View.FOCUS_DOWN);
        assertTrue(near < far);
    }

    @Test public void beamOverlapWorksInBothAxes() {
        assertTrue(TvSpatialNavigation.SpatialScore.overlapsBeam(
                100, 100, 200, 200, 150, 225, 250, 325, View.FOCUS_DOWN));
        assertFalse(TvSpatialNavigation.SpatialScore.overlapsBeam(
                100, 100, 200, 200, 215, 225, 315, 325, View.FOCUS_DOWN));
    }
}
