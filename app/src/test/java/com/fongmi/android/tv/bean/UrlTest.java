package com.fongmi.android.tv.bean;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UrlTest {

    @Test
    public void selectingQualityOnAnEmptyResultLeavesAnEmptyUrl() {
        Url url = Url.create().set(0);

        assertEquals(0, url.getPosition());
        assertEquals("", url.v());
        assertEquals("", url.n(-1));
        assertTrue(url.isEmpty());
    }

    @Test
    public void missingQualityEntryDoesNotCrash() {
        Url url = Url.create();
        url.getValues().add(null);

        assertEquals("", url.v(-1));
        assertEquals("", url.n(0));
        assertEquals("", url.v());
    }
}
