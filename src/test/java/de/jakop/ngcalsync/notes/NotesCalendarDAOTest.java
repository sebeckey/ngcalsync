package de.jakop.ngcalsync.notes;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.bea.domingo.DDatabase;
import de.bea.domingo.DDocument;
import de.bea.domingo.DView;
import de.bea.domingo.DViewEntry;
import de.bea.domingo.util.GregorianDateTime;
import de.jakop.ngcalsync.calendar.CalendarEvent;
import de.jakop.ngcalsync.filter.ICalendarEventFilter;

/**
 * 
 * @author fjakop
 *
 */
public class NotesCalendarDAOTest {

	@Mock
	private DDatabase database;
	@Mock
	private DView view;

	@Mock
	private IOpenDatabaseStrategy openDatabaseStrategy;

	/** */
	@Before
	public void before() {
		MockitoAnnotations.initMocks(this);
		when(openDatabaseStrategy.openDatabase(Matchers.anyString(), Matchers.anyString())).thenReturn(database);
		when(database.getView(Matchers.eq("Calendar"))).thenReturn(view);

	}

	/**
	 * 
	 */
	@Test
	public void testRecurringEntriesDates() {
		final Calendar start = Calendar.getInstance();
		final Calendar end = Calendar.getInstance();

		start.set(2012, 4, 1);
		end.set(2012, 4, 30);

		final List<DViewEntry> viewEntries = new ArrayList<DViewEntry>();
		final List<GregorianDateTime> starts = new ArrayList<GregorianDateTime>();
		for (int i = 1; i <= 3; i++) {
			final GregorianDateTime startDateTime = new GregorianDateTime(Calendar.getInstance());
			final GregorianDateTime endDateTime = new GregorianDateTime(Calendar.getInstance());
			startDateTime.set(2012, 4, i, 2, 22);
			endDateTime.set(2012, 4, i, 3, 0);

			starts.add(startDateTime);

			final List<Calendar> listStart = new ArrayList<Calendar>();
			listStart.add(startDateTime);
			final List<Calendar> listEnd = new ArrayList<Calendar>();
			listEnd.add(endDateTime);

			final DDocument document = mock(DDocument.class);
			when(document.getItemValueString("Form")).thenReturn("Appointment");
			when(document.getItemValueString("AppointmentType")).thenReturn("3");
			when(document.getItemValue("Room")).thenReturn(Arrays.asList(""));
			when(document.getItemValue("Location")).thenReturn(Arrays.asList(""));
			when(document.getItemValue("Body")).thenReturn(Arrays.asList(""));
			when(document.getItemValue("Subject")).thenReturn(Arrays.asList(""));
			when(document.getItemValue("CalendarDateTime")).thenReturn(starts);
			when(document.getItemValue("EndDateTime")).thenReturn(Arrays.asList(endDateTime));
			when(document.getItemValue("StartDateTime")).thenReturn(Arrays.asList(startDateTime));
			when(Boolean.valueOf(document.hasItem("Repeats"))).thenReturn(Boolean.TRUE);
			when(document.getItemValue("Repeats")).thenReturn(Arrays.asList("1"));
			when(document.getItemValue("OrgConfidential")).thenReturn(Arrays.asList(""));

			final DViewEntry viewEntry = mock(DViewEntry.class);
			when(viewEntry.getDocument()).thenReturn(document);
			viewEntries.add(viewEntry);
		}

		when(view.getAllEntriesByKey(start, end, false)).thenReturn(viewEntries.iterator());

		final INotesCalendarDAO dao = new NotesCalendarDAO(openDatabaseStrategy, "", "", start, end);
		final List<CalendarEvent> entries = dao.getEntries(new ICalendarEventFilter[] {});

		// Event #1 is missing, because of Notes' date filter
		assertEquals(2, entries.size());

		assertEquals(starts.get(1).getTime(), entries.get(0).getStartDateTime().getTime());
		assertEquals(starts.get(2).getTime(), entries.get(1).getStartDateTime().getTime());
	}
}
