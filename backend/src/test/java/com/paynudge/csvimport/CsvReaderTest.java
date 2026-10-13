package com.paynudge.csvimport;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.paynudge.common.ApiException;
import com.paynudge.csvimport.CsvReader.CsvRow;
import com.paynudge.csvimport.CsvReader.CsvTable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvReaderTest {

	@Test
	void readsHeadersAndRowsWithSpreadsheetRowNumbers() {
		CsvTable table = read("""
				Name,Email,Amount

				 Aarav Shah , aarav@example.com ,"1,500"
				Diya,diya@example.com,2000
				""");

		assertThat(table.headers()).containsExactly("Name", "Email", "Amount");
		assertThat(table.rows()).containsExactly(new CsvRow(3, List.of("Aarav Shah", "aarav@example.com", "1,500")),
				new CsvRow(4, List.of("Diya", "diya@example.com", "2000")));
	}

	@Test
	void excelsByteOrderMarkIsNotPartOfTheFirstHeader() {
		byte[] bom = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
		byte[] body = "Name,Amount\nAarav,1500\n".getBytes(StandardCharsets.UTF_8);
		byte[] file = new byte[bom.length + body.length];
		System.arraycopy(bom, 0, file, 0, bom.length);
		System.arraycopy(body, 0, file, bom.length, body.length);

		assertThat(CsvReader.read(file).headers()).containsExactly("Name", "Amount");
	}

	@Test
	void windows1252FilesFromOlderExcelAreDecoded() {
		byte[] file = "Name,City\nZoë,Café\n".getBytes(Charset.forName("windows-1252"));

		assertThat(CsvReader.read(file).rows().get(0).cells()).containsExactly("Zoë", "Café");
	}

	@Test
	void semicolonAndTabSeparatedFilesAreDetected() {
		assertThat(read("Name;Amount\nAarav;1,500\n").rows().get(0).cells()).containsExactly("Aarav", "1,500");
		assertThat(read("Name\tAmount\nAarav\t1500\n").rows().get(0).cells()).containsExactly("Aarav", "1500");
	}

	@Test
	void excelsSepLineChoosesTheSeparator() {
		CsvTable table = read("sep=;\nName;Amount\nAarav;1500\n");

		assertThat(table.headers()).containsExactly("Name", "Amount");
		assertThat(table.rows().get(0).number()).isEqualTo(3);
	}

	@Test
	void blankAndRepeatedHeadersGetUsableNames() {
		assertThat(read("Name,,name,Amount\na,b,c,1\n").headers()).containsExactly("Name", "Column 2", "name (2)",
				"Amount");
	}

	@Test
	void shortRowsReadAsEmptyCells() {
		CsvRow row = read("Name,Email,Amount\nAarav\n").rows().get(0);

		assertThat(row.cell(0)).isEqualTo("Aarav");
		assertThat(row.cell(2)).isEmpty();
	}

	@Test
	void unreadableFilesSayWhatToDo() {
		assertProblem(new byte[0], "EMPTY_FILE", "This file is empty.");
		assertProblem(bytes("\n \n"), "EMPTY_FILE", "This file is empty.");
		assertProblem(bytes("Name,Amount\n\n"), "NO_ROWS", "This file has a header row but no rows under it.");
		assertProblem(new byte[] { 0x50, 0x4B, 0x03, 0x04, 0x14 }, "NOT_CSV",
				"This looks like an Excel workbook. In Excel, choose File > Save As > CSV, then upload that file.");
		assertProblem(bytes("Name,Amount\n\"Aarav,1500\n"), "NOT_CSV",
				"We couldn't read this file as CSV. Check for a quote (\") that is opened but never closed.");
	}

	@Test
	void filesAreLimitedToFiveThousandRows() {
		StringBuilder csv = new StringBuilder("Name,Amount\n");
		csv.append("a,1\n".repeat(CsvReader.MAX_ROWS));
		assertThat(read(csv.toString()).rows()).hasSize(CsvReader.MAX_ROWS);

		csv.append("a,1\n");
		assertProblem(bytes(csv.toString()), "TOO_MANY_ROWS",
				"This file has more than 5,000 rows. Split it into smaller files.");
	}

	private static CsvTable read(String csv) {
		return CsvReader.read(bytes(csv));
	}

	private static byte[] bytes(String csv) {
		return csv.getBytes(StandardCharsets.UTF_8);
	}

	private static void assertProblem(byte[] file, String code, String message) {
		assertThatThrownBy(() -> CsvReader.read(file)).isInstanceOfSatisfying(ApiException.class,
				(ex) -> assertThat(ex.code()).isEqualTo(code))
			.hasMessage(message);
	}

}
