package net.sourceforge.plantuml.svek;

import org.graphper.api.attributes.Labeljust;
import org.graphper.api.attributes.Rank;

public class GraphSupportHelper {

	private GraphSupportHelper() {
	}

	public static Labeljust toLabeljust(String labeljust) {
		for (Labeljust l : Labeljust.values()) {
			if (l.name().equalsIgnoreCase(labeljust)) {
				return l;
			}
		}

		return Labeljust.CENTER;
	}

	public static Rank toRank(String rank) {
		for (Rank r : Rank.values()) {
			if (r.name().equalsIgnoreCase(rank)) {
				return r;
			}
		}

		return null;
	}
}
