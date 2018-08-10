/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013, Christian Schudt
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 *
 *
 */
package org.sleuthkit.autopsy.timeline.ui.detailview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.scene.chart.Axis;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.utils.RangeDivision;

/**
 * from <a
 * href="https://bitbucket.org/sco0ter/extfx/src/a61710e99c63bfa288672f0d99861c8fe8571293/src/main/java/extfx/scene/chart/DateAxis.java?at=0.3"
 * >here</a>
 *
 * <strong> with extreme modifications.</strong>
 *
 * @author Christian Schudt
 * @author Diego Cirujano
 */
final class DateAxis extends Axis<DateTime> {

    private ObjectProperty<DateTime> lowerBound = new ObjectPropertyBase<DateTime>(new DateTime(0)) {
        @Override
        protected void invalidated() {
            if (!isAutoRanging()) {
                invalidateRange();
                requestAxisLayout();
            }
        }

        @Override
        public Object getBean() {
            return DateAxis.this;
        }

        @Override
        public String getName() {
            return "lowerBound"; // NON-NLS
        }
    };

    /**
     * Stores the min and max date of the list of dates which is used. If
     * {@link #autoRanging} is true, these values are used as lower and upper
     * bounds.
     */
    private DateTime maxDate;

    /**
     * Stores the min and max date of the list of dates which is used. If
     * {@link #autoRanging} is true, these values are used as lower and upper
     * bounds.
     */
    private DateTime minDate;

    private RangeDivision rangeDivisionInfo;

    private final ReadOnlyDoubleWrapper tickSpacing = new ReadOnlyDoubleWrapper();

    private final ObjectProperty<DateTime> upperBound = new ObjectPropertyBase<DateTime>(new DateTime(1)) {
        @Override
        protected void invalidated() {
            if (!isAutoRanging()) {
                invalidateRange();
                requestAxisLayout();
            }
        }

        @Override
        public Object getBean() {
            return DateAxis.this;
        }

        @Override
        public String getName() {
            return "upperBound"; // NON-NLS
        }
    };

    /**
     * Default constructor. By default the lower and upper bound are calculated
     * by the data.
     */
    DateAxis() {
        setTickLabelGap(0);
        setAutoRanging(false);
        setTickLabelsVisible(false);
        setTickLength(0);
        setTickMarkVisible(false);
    }

    @Override
    public double getDisplayPosition(DateTime date) {
        final double length = -200 + (getSide().isHorizontal() ? getWidth() : getHeight());

        // Get the difference between the max and min date.
        double diff = getUpperBound().getMillis() - getLowerBound().getMillis();

        // Get the actual range of the visible area.
        // The minimal date should start at the zero position, that's why we subtract it.
        double range = length - getZeroPosition();

        // Then get the difference from the actual date to the min date and divide it by the total difference.
        // We get a value between 0 and 1, if the date is within the min and max date.
        double d = (date.getMillis() - getLowerBound().getMillis()) / diff;

        // Multiply this percent value with the range and add the zero offset.
        if (getSide().isVertical()) {
            return getHeight() - d * range + getZeroPosition();
        } else {
            return d * range + getZeroPosition();
        }
    }

    /**
     * Gets the lower bound of the axis.
     *
     * @return The lower bound.
     *
     * @see #lowerBoundProperty()
     */
    public DateTime getLowerBound() {
        return lowerBound.get();
    }

    /**
     * Sets the lower bound of the axis.
     *
     * @param date The lower bound date.
     *
     * @see #lowerBoundProperty()
     */
    public void setLowerBound(DateTime date) {
        lowerBound.set(date);
    }

    /**
     * Gets the upper bound of the axis.
     *
     * @return The upper bound.
     *
     * @see #upperBoundProperty()
     */
    public DateTime getUpperBound() {
        return upperBound.get();
    }

    /**
     * Sets the upper bound of the axis.
     *
     * @param date The upper bound date.
     *
     * @see #upperBoundProperty() ()
     */
    public void setUpperBound(DateTime date) {
        upperBound.set(date);
    }

    @Override
    public DateTime getValueForDisplay(double displayPosition) {
        final double length = - 200 + (getSide().isHorizontal() ? getWidth() : getHeight());

        // Get the difference between the max and min date.
        double diff = getUpperBound().getMillis() - getLowerBound().getMillis();

        // Get the actual range of the visible area.
        // The minimal date should start at the zero position, that's why we subtract it.
        double range = length - getZeroPosition();

        if (getSide().isVertical()) {
            // displayPosition = getHeight() - ((date - lowerBound) / diff) * range + getZero
            // date = displayPosition - getZero - getHeight())/range * diff + lowerBound
            return new DateTime((long) ((displayPosition - getZeroPosition() - getHeight()) / -range * diff + getLowerBound().getMillis()), TimeLineController.getJodaTimeZone());
        } else {
            // displayPosition = ((date - lowerBound) / diff) * range + getZero
            // date = displayPosition - getZero)/range * diff + lowerBound
            return new DateTime((long) ((displayPosition - getZeroPosition()) / range * diff + getLowerBound().getMillis()), TimeLineController.getJodaTimeZone());
        }
    }

    @Override
    public double getZeroPosition() {
        return 0;
    }

    @Override
    public void invalidateRange(List<DateTime> list) {
        super.invalidateRange(list);

        Collections.sort(list);
        if (list.isEmpty()) {
            minDate = maxDate = new DateTime();
        } else if (list.size() == 1) {
            minDate = maxDate = list.get(0);
        } else if (list.size() > 1) {
            minDate = list.get(0);
            maxDate = list.get(list.size() - 1);
        }
    }

    @Override
    public boolean isValueOnAxis(DateTime date) {
        return date.getMillis() > getLowerBound().getMillis() && date.getMillis() < getUpperBound().getMillis();
    }

    @Override
    public double toNumericValue(DateTime date) {
        return date.getMillis();
    }

    @Override
    public DateTime toRealValue(double v) {
        return new DateTime((long) v);
    }

    @Override
    protected Interval autoRange(double length) {
        if (isAutoRanging()) {
            return new Interval(minDate, maxDate);
        } else {
            if (getLowerBound() == null || getUpperBound() == null) {
                return null;
            }
            return getRange();
        }
    }

    @Override
    protected List<DateTime> calculateTickValues(double length, Object range) {
        List<DateTime> tickDates = new ArrayList<>();
        if (range == null) {
            return tickDates;
        }
        rangeDivisionInfo = RangeDivision.getRangeDivision((Interval) range, TimeLineController.getJodaTimeZone());
        final DateTime lowerBound1 = getLowerBound();
        final DateTime upperBound1 = getUpperBound();

        if (lowerBound1 == null || upperBound1 == null) {
            return tickDates;
        }
        DateTime lower = lowerBound1.withZone(TimeLineController.getJodaTimeZone());
        DateTime upper = upperBound1.withZone(TimeLineController.getJodaTimeZone());

        DateTime current = lower;
        // Loop as long we exceeded the upper bound.
        while (current.isBefore(upper)) {
            tickDates.add(current);
            current = current.plus(rangeDivisionInfo.getPeriodSize().toUnitPeriod());//.add(interval.interval, interval.amount);
        }

        // At last add the upper bound.
        tickDates.add(upper);

        // If there are at least three dates, check if the gap between the lower date and the second date is at least half the gap of the second and third date.
        // Do the same for the upper bound.
        // If gaps between dates are to small, remove one of them.
        // This can occur, e.g. if the lower bound is 25.12.2013 and years are shown. Then the next year shown would be 2014 (01.01.2014) which would be too narrow to 25.12.2013.
        if (tickDates.size() > 2) {
            DateTime secondDate = tickDates.get(1);
            DateTime thirdDate = tickDates.get(2);
            DateTime lastDate = tickDates.get(tickDates.size() - 2);
            DateTime previousLastDate = tickDates.get(tickDates.size() - 3);

            // If the second date is too near by the lower bound, remove it.
            if (secondDate.getMillis() - lower.getMillis() < (thirdDate.getMillis() - secondDate.getMillis()) / 2) {
                tickDates.remove(lower);
            }

            // If difference from the upper bound to the last date is less than the half of the difference of the previous two dates,
            // we better remove the last date, as it comes to close to the upper bound.
            if (upper.getMillis() - lastDate.getMillis() < (lastDate.getMillis() - previousLastDate.getMillis()) / 2) {
                tickDates.remove(lastDate);
            }
        }

        if (tickDates.size() >= 2) {
            tickSpacing.set(getDisplayPosition(tickDates.get(1)) - getDisplayPosition(tickDates.get(0)));
        } else if (tickDates.size() >= 4) {
            tickSpacing.set(getDisplayPosition(tickDates.get(2)) - getDisplayPosition(tickDates.get(1)));
        }
        return tickDates;
    }

    @Override
    protected Interval getRange() {
        return new Interval(getLowerBound(), getUpperBound());
    }

    @Override
    protected String getTickMarkLabel(DateTime date) {
        return rangeDivisionInfo.getTickFormatter().print(date);
    }

    /**
     *
     * @param range     an {@link Interval}
     * @param animating ignored
     */
    @Override
    protected void setRange(Object range, boolean animating) {
        rangeDivisionInfo = RangeDivision.getRangeDivision((Interval) range, TimeLineController.getJodaTimeZone());
        setLowerBound(new DateTime(rangeDivisionInfo.getLowerBound(), TimeLineController.getJodaTimeZone()));
        setUpperBound(new DateTime(rangeDivisionInfo.getUpperBound(), TimeLineController.getJodaTimeZone()));
    }

    ReadOnlyDoubleProperty getTickSpacing() {
        return tickSpacing.getReadOnlyProperty();
    }
}
