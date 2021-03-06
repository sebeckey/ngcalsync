package de.jakop.ngcalsync.google;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar.Events.Insert;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Event.Reminders;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;
import com.google.api.services.calendar.model.Events;

import de.jakop.ngcalsync.calendar.CalendarEvent;
import de.jakop.ngcalsync.calendar.EventType;
import de.jakop.ngcalsync.exception.SynchronisationException;
import de.jakop.ngcalsync.filter.ICalendarEventFilter;
import de.jakop.ngcalsync.i18n.LocalizedTechnicalStrings.TechMessage;
import de.jakop.ngcalsync.i18n.LocalizedUserStrings.UserMessage;
import de.jakop.ngcalsync.settings.Settings;

/**
 * Accesses Google calendar events 
 * 
 * @author fjakop
 *
 */
class GoogleCalendarDAO implements IGoogleCalendarDAO {


	private final Log log = LogFactory.getLog(getClass());

	private final com.google.api.services.calendar.Calendar service;

	private final Settings settings;

	private com.google.api.services.calendar.model.Calendar calendar;

	private final static SimpleDateFormat dateFormatDateOnly = new SimpleDateFormat("yyyy-MM-dd"); //$NON-NLS-1$


	/**
	 * 
	 * @param settings
	 */
	GoogleCalendarDAO(final Settings settings) {
		this.settings = settings;
		service = settings.getGoogleCalendarService();
	}


	@Override
	public String insert(final CalendarEvent event) {
		log.debug(TechMessage.get().MSG_EXECUTING_INSERT(event.getTitle()));

		final Event myEvent = new Event();
		updateCalendarEventData(event, myEvent);

		Event insertedEvent;
		try {
			final Insert insert = settings.getGoogleCalendarService().events().insert(getCalendar().getId(), myEvent);
			insertedEvent = insert.execute();

			final String id = insertedEvent.getId();
			return id;
		} catch (final IOException e) {
			throw new SynchronisationException(e);
		}
	}

	@Override
	public void update(final String id, final CalendarEvent event) {
		log.debug(TechMessage.get().MSG_EXECUTING_UPDATE(event.getTitle()));

		try {
			final Event myEvent = service.events().get(getCalendar().getId(), id).execute();
			updateCalendarEventData(event, myEvent);
			service.events().update(getCalendar().getId(), id, myEvent).execute();
		} catch (final IOException e) {
			throw new SynchronisationException(e);
		}
	}

	@Override
	public void delete(final String id) {
		log.debug(TechMessage.get().MSG_EXECUTING_DELETE(id));

		try {
			service.events().delete(getCalendar().getId(), id).execute();
		} catch (final IOException e) {
			throw new SynchronisationException(e);
		}

	}

	@Override
	public List<CalendarEvent> getEvents(final ICalendarEventFilter[] filters) throws SynchronisationException {
		log.info(UserMessage.get().MSG_READING_GOOGLE_EVENTS(getCalendar().getSummary()));

		final List<CalendarEvent> events = new ArrayList<CalendarEvent>();
		try {

			final Calendar sdt = settings.getSyncStartDate();
			final Calendar edt = settings.getSyncEndDate();

			// datetimes are in RFC3339-format
			final String startDateTime = new DateTime(sdt.getTime(), sdt.getTimeZone()).toStringRfc3339();
			final String endDateTime = new DateTime(edt.getTime(), edt.getTimeZone()).toStringRfc3339();

			final Events googleEvents = service.events().list(getCalendar().getId())//
					.setTimeMin(startDateTime).setTimeMax(endDateTime)//
					.setMaxResults(new Integer(65535))//
					.setOrderBy("starttime")// //$NON-NLS-1$
					// handling recurrence is not necessary, since Lotus Notes recurrence is a pain in the a..
					.setSingleEvents(Boolean.TRUE)//
					.execute();

			// if no entry is present in the Google calendar, the list is null
			if (googleEvents.getItems() == null) {
				return new ArrayList<CalendarEvent>();
			}
			for (final Event googleEvent : googleEvents.getItems()) {
				events.add(convGoogleEvent(googleEvent));
			}
		} catch (final IOException e) {
			throw new SynchronisationException(e);
		} catch (final ParseException e) {
			throw new SynchronisationException(e);
		}

		return events;

	}

	/**
	 * retrieves the calendar for the given name (summary)
	 *  
	 * @return den Kalender
	 */
	private com.google.api.services.calendar.model.Calendar getCalendar() {
		if (calendar == null) {
			final com.google.api.services.calendar.Calendar myService = settings.getGoogleCalendarService();
			try {
				final CalendarList list = myService.calendarList().list().execute();
				for (final CalendarListEntry entry : list.getItems()) {
					if (settings.getGoogleCalendarName().equals(entry.getSummary())) {
						final String id = entry.getId();
						calendar = myService.calendars().get(id).execute();
						break;
					}
				}
			} catch (final IOException e) {
				throw new SynchronisationException(e);
			}

			if (calendar == null) {
				// calendar with given name does not exist
				throw new SynchronisationException(UserMessage.get().GOOGLE_CALENDAR_S_DOES_NOT_EXIST_CHECK_CONFIG(settings.getGoogleCalendarName()));
			}
		}
		return calendar;
	}

	private void updateCalendarEventData(final CalendarEvent event, final Event googleEvent) {
		googleEvent.setSummary(event.getTitle());
		googleEvent.setDescription(event.getContent());

		final Calendar sdt = event.getStartDateTime();
		final EventDateTime startTime = new EventDateTime();
		startTime.setDateTime(new DateTime(sdt.getTime(), sdt.getTimeZone()));

		final Calendar edt = event.getEndDateTime();
		final EventDateTime endTime = new EventDateTime();
		endTime.setDateTime(new DateTime(edt.getTime(), edt.getTimeZone()));

		if (event.isAllDay()) {

			edt.setTimeInMillis(sdt.getTimeInMillis());
			edt.add(Calendar.DAY_OF_YEAR, 1);

			startTime.setDate(dateFormatDateOnly.format(sdt.getTime()));
			endTime.setDate(dateFormatDateOnly.format(edt.getTime()));

			startTime.setDateTime(null);
			endTime.setDateTime(null);
		}

		googleEvent.setStart(startTime);
		googleEvent.setEnd(endTime);

		googleEvent.setLocation(event.getLocation());

		final Reminders reminders = new Reminders();
		final EventReminder eventReminder = new EventReminder();
		eventReminder.setMinutes(new Integer(settings.getReminderMinutes()));
		eventReminder.setMethod("popup"); //$NON-NLS-1$
		reminders.setOverrides(Arrays.asList(eventReminder));
		reminders.setUseDefault(Boolean.FALSE);
		googleEvent.setReminders(reminders);

	}

	private CalendarEvent convGoogleEvent(final Event googleEvent) throws ParseException {

		final CalendarEvent myEvent = new CalendarEvent();
		myEvent.setTitle(googleEvent.getSummary());
		myEvent.setContent(googleEvent.getDescription());
		myEvent.setId(googleEvent.getId());

		myEvent.setLocation(googleEvent.getLocation());
		final Calendar u = Calendar.getInstance();
		u.setTimeInMillis(googleEvent.getUpdated().getValue());
		myEvent.setLastUpdated(u);

		// Visibility visibility = entry.getVisibility();

		DateTime sdt = null;
		DateTime edt = null;
		if (googleEvent.getStart().getDateTime() == null) {
			// all day event - no DateTime, only Date present
			myEvent.setEventType(EventType.ALL_DAY_EVENT);
			sdt = new DateTime(dateFormatDateOnly.parse(googleEvent.getStart().getDate()));
			edt = new DateTime(dateFormatDateOnly.parse(googleEvent.getEnd().getDate()));
		} else {
			// "timed" event - DateTime present
			sdt = googleEvent.getStart().getDateTime();
			edt = googleEvent.getEnd().getDateTime();

			if (sdt.getValue() == edt.getValue()) {
				myEvent.setEventType(EventType.REMINDER);
			} else {
				myEvent.setEventType(EventType.NORMAL_EVENT);
			}
		}

		final Calendar sdtCalendar = Calendar.getInstance();
		sdtCalendar.setTimeInMillis(sdt.getValue());
		myEvent.setStartDateTime(sdtCalendar);

		final Calendar edtCalendar = Calendar.getInstance();
		edtCalendar.setTimeInMillis(edt.getValue());
		myEvent.setEndDateTime(edtCalendar);

		return myEvent;
	}

}
