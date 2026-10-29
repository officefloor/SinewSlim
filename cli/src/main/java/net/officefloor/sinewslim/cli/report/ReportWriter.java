package net.officefloor.sinewslim.cli.report;

import java.io.IOException;
import java.util.Locale;

/** Writes a scan in one format. */
public interface ReportWriter {

	/**
	 * @param near list the methods under the cap within this many lines of the trigger, in the
	 *             formats that do not list every method
	 */
	void write(ScanReport report, int near, Appendable out) throws IOException;

	enum Format {
		TEXT(new TextReport()), MARKDOWN(new MarkdownReport()), HTML(new HtmlReport()), JSON(new JsonReport()),
		CSV(new CsvReport()), SARIF(new SarifReport());

		private final ReportWriter writer;

		Format(ReportWriter writer) {
			this.writer = writer;
		}

		public ReportWriter writer() {
			return writer;
		}

		public static Format of(String name) {
			return valueOf(name.toUpperCase(Locale.ROOT));
		}
	}
}
