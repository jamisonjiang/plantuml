/* ========================================================================
 * PlantUML : a free UML diagram generator
 * ========================================================================
 *
 * (C) Copyright 2009-2024, Arnaud Roques
 *
 * Project Info:  https://plantuml.com
 *
 * If you like this project or if you find it useful, you can support us at:
 *
 * https://plantuml.com/patreon (only 1$ per month!)
 * https://plantuml.com/paypal
 *
 * This file is part of PlantUML.
 *
 * PlantUML is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PlantUML distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public
 * License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 *
 * Original Author:  Arnaud Roques
 * Contribution :  Hisashi Miyashita
 *
 *
 */
package net.sourceforge.plantuml.svek;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import net.sourceforge.plantuml.StringUtils;
import net.sourceforge.plantuml.abel.EntityPosition;
import net.sourceforge.plantuml.decoration.symbol.USymbols;
import net.sourceforge.plantuml.dot.GraphvizVersion;
import net.sourceforge.plantuml.klimt.font.StringBounder;
import net.sourceforge.plantuml.klimt.geom.HorizontalAlignment;
import net.sourceforge.plantuml.skin.AlignmentParam;
import net.sourceforge.plantuml.skin.UmlDiagramType;
import net.sourceforge.plantuml.style.ISkinParam;
import net.sourceforge.plantuml.svek.DotStringFactory.GraphvizContext;

import org.graphper.api.Cluster.ClusterBuilder;
import org.graphper.api.Cluster.IntegrationClusterBuilder;
import org.graphper.api.GraphContainer.GraphContainerBuilder;
import org.graphper.api.Graphviz;
import org.graphper.api.Graphviz.GraphvizBuilder;
import org.graphper.api.Html.Table;
import org.graphper.api.Line;
import org.graphper.api.Node;
import org.graphper.api.Node.NodeBuilder;
import org.graphper.api.Subgraph;
import org.graphper.api.Subgraph.SubgraphBuilder;
import org.graphper.api.attributes.NodeShapeEnum;
import org.graphper.api.attributes.Rank;

public class ClusterDotString {
	// ::remove file when __CORE__

	private final Cluster cluster;
	private final ISkinParam skinParam;
	private static final String ID_EE = "ee";

	public ClusterDotString(Cluster cluster, ISkinParam skinParam) {
		this.cluster = cluster;
		this.skinParam = skinParam;
	}

	private boolean isPacked() {
		return cluster.getGroup().isPacked();
	}

	void printInternal(StringBuilder sb, Collection<SvekLine> lines, StringBounder stringBounder, DotMode dotMode,
			GraphvizVersion graphvizVersion, UmlDiagramType type, GraphvizContext graphvizContext) {
		if (cluster.diagram.getPragma().useKermor()) {
			new ClusterDotStringKermor(cluster, skinParam).printInternal(sb, lines, stringBounder, dotMode,
					graphvizVersion, type, graphvizContext);
			return;
		}
		final boolean packed = isPacked();

		if (packed) {
			cluster.printCluster1(sb, lines, stringBounder);
			final SvekNode added = cluster.printCluster2(sb, lines, stringBounder, dotMode, graphvizVersion, type, graphvizContext);
			return;

		}
		final boolean thereALinkFromOrToGroup2 = isThereALinkFromOrToGroup(lines);
		boolean thereALinkFromOrToGroup1 = thereALinkFromOrToGroup2;
		final boolean useProtectionWhenThereALinkFromOrToGroup = graphvizVersion
				.useProtectionWhenThereALinkFromOrToGroup();
		if (useProtectionWhenThereALinkFromOrToGroup == false)
			thereALinkFromOrToGroup1 = false;

		Deque<ClusterBuilder> clusterStack = new LinkedList<>();
		ClusterBuilder currentCluster;
		ClusterBuilder thereALinkFromOrToGroup1Cluster_1 = null;
		if (thereALinkFromOrToGroup1) {
			subgraphClusterNoLabel(sb, "a");
			thereALinkFromOrToGroup1Cluster_1 = org.graphper.api.Cluster
							.builder().id(cluster.getClusterId() + "a");
			currentCluster = thereALinkFromOrToGroup1Cluster_1;
			clusterStack.offerLast(currentCluster);
		}

		final Set<EntityPosition> entityPositionsExceptNormal = entityPositionsExceptNormal();
		if (entityPositionsExceptNormal.size() > 0)
			for (SvekLine line : lines)
				if (line.isLinkFromOrTo(cluster.getGroup()))
					line.setProjectionCluster(cluster);

		boolean protection0 = protection0(type);
		boolean protection1 = protection1(type);
		if (entityPositionsExceptNormal.size() > 0 || useProtectionWhenThereALinkFromOrToGroup == false) {
			protection0 = false;
			protection1 = false;
		}

		ClusterBuilder protection0Cluster = null;
		if (protection0) {
			protection0Cluster = org.graphper.api.Cluster.builder();
			currentCluster = protection0Cluster;
			clusterStack.offerLast(currentCluster);
			subgraphClusterNoLabel(sb, "p0");
			protection0Cluster.id(cluster.getClusterId() + "p0").label("");
		}

		currentCluster = org.graphper.api.Cluster.builder();
		clusterStack.offerLast(currentCluster);

		sb.append("subgraph " + cluster.getClusterId() + " {");
		sb.append("style=solid;");
		sb.append("color=\"" + StringUtils.sharp000000(cluster.getColor()) + "\";");
		currentCluster.id(StringUtils.sharp000000(cluster.getColor()));

		final String label;
		Table table;
		if (cluster.isLabel()) {
			final StringBuilder sblabel = new StringBuilder("<");
			table = SvekLine.appendTable(sblabel, cluster.getTitleAndAttributeWidth(),
			                                   cluster.getTitleAndAttributeHeight() - 5,
			                                   cluster.getTitleColor());
			sblabel.append(">");
			label = sblabel.toString();
			final HorizontalAlignment align = skinParam.getHorizontalAlignment(AlignmentParam.packageTitleAlignment,
					null, false, null);
			sb.append("labeljust=\"" + align.getGraphVizValue() + "\";");
			currentCluster.labeljust(GraphSupportHelper.toLabeljust(align.getGraphVizValue()));
		} else {
			label = "\"\"";
			table = null;
		}

		if (entityPositionsExceptNormal.size() > 0) {
			GraphContainerBuilder p = graphvizContext.getCurrentBuilder();
			graphvizContext.setCurrentBuilder(currentCluster);
			printRanks(Cluster.RANK_SOURCE, withPosition(EntityPosition.getInputs()), sb, stringBounder, graphvizContext);
			printRanks(Cluster.RANK_SINK, withPosition(EntityPosition.getOutputs()), sb, stringBounder, graphvizContext);
			graphvizContext.setCurrentBuilder(p);
			currentCluster = org.graphper.api.Cluster.builder();
			clusterStack.offerLast(currentCluster);
			if (hasPort())
				subgraphClusterNoLabel(sb, ID_EE);
			else {
				subgraphClusterWithLabel(sb, ID_EE, label);
				currentCluster.table(table);
			}

			currentCluster.id(cluster.getClusterId() + ID_EE);
		} else {
			currentCluster.table(table);
			sb.append("label=" + label + ";");
			SvekUtils.println(sb);
		}

		if (thereALinkFromOrToGroup2) {
			String id = Cluster.getSpecialPointId(cluster.getGroup());
			sb.append(id + " [shape=point,width=.01,label=\"\"];");
			Node n = graphvizContext.getIfAbsent(id, s -> Node.builder()
							.id(id).shape(NodeShapeEnum.POINT).width(.01).height(.01)
							.margin(0, 0).build());
			currentCluster.addNode(n);
		}

		ClusterBuilder thereALinkFromOrToGroup1Cluster_2 = null;
		if (thereALinkFromOrToGroup1) {
			thereALinkFromOrToGroup1Cluster_2 = org.graphper.api.Cluster.builder();
			currentCluster = thereALinkFromOrToGroup1Cluster_2;
			clusterStack.offerLast(currentCluster);
			subgraphClusterNoLabel(sb, "i");
			currentCluster.id(cluster.getClusterId() + "i");

//			sb.append("subgraph " + cluster.getClusterId() + id + " {");
//			sb.append("label=" + label + ";");
		}

		ClusterBuilder protection1Cluster = null;
		if (protection1) {
			subgraphClusterNoLabel(sb, "p1");
			protection1Cluster = org.graphper.api.Cluster.builder();
			currentCluster = protection1Cluster;
			clusterStack.offerLast(currentCluster);
			protection1Cluster.id(cluster.getClusterId() + "p1");
		}

		if (skinParam.useSwimlanes(type)) {
			SubgraphBuilder subgraph = Subgraph.builder();
			sb.append("{rank = source; ");
			subgraph.rank(Rank.SOURCE);
			String sourceInPoint = getSourceInPoint(type);
			sb.append(sourceInPoint);
			sb.append(" [shape=point,width=.01,label=\"\"];");
			Node sip = Node.builder().id(sourceInPoint).shape(NodeShapeEnum.POINT).width(0.01).build();

			String minPoint = cluster.getMinPoint(type);
			sb.append(minPoint + "->" + getSourceInPoint(type) + "  [weight=999];");
			subgraph.addLine(Line.builder(Node.builder().label(minPoint).build(), sip).weight(999).build());
			sb.append("}");
			currentCluster.subgraph(subgraph.build());
			SvekUtils.println(sb);
			subgraph = Subgraph.builder();
			sb.append("{rank = sink; ");
			subgraph.rank(Rank.SINK);
			String sinkInPoint = getSinkInPoint(type);
			sb.append(sinkInPoint);
			sb.append(" [shape=point,width=.01,label=\"\"];");
			Node sinkP = Node.builder().id(sinkInPoint).shape(NodeShapeEnum.POINT).width(0.01).build();
			subgraph.addNode(sinkP);
			sb.append("}");
			currentCluster.subgraph(subgraph.build());

			String maxPoint = cluster.getMaxPoint(type);
			sb.append(getSinkInPoint(type) + "->" + maxPoint + "  [weight=999];");
			currentCluster.addLine(Line.builder(sinkP, Node.builder().id(maxPoint).label(maxPoint).build()).weight(999).build());
			SvekUtils.println(sb);
		}
		SvekUtils.println(sb);

		// -----------
		cluster.printCluster1(sb, lines, stringBounder);

		GraphContainerBuilder parent = graphvizContext.getCurrentBuilder();
		graphvizContext.setCurrentBuilder(currentCluster);
		final SvekNode added = cluster.printCluster2(sb, lines, stringBounder, dotMode, graphvizVersion, type, graphvizContext);
		graphvizContext.setCurrentBuilder(parent);
		if (entityPositionsExceptNormal.size() > 0)
			if (hasPort()) {
				sb.append(empty() + " [shape=rect,width=.01,height=.01,label=");
				sb.append(label);
				sb.append("];");
			} else if (added == null) {
				sb.append(empty() + " [shape=point,width=.01,label=\"\"];");
			}
		SvekUtils.println(sb);

		// -----------

		sb.append("}");
		if (protection1) {
			sb.append("}");
		}


		if (thereALinkFromOrToGroup1) {
			sb.append("}");
			sb.append("}");
		}
		if (entityPositionsExceptNormal.size() > 0)
			sb.append("}");

		if (protection0) {
			sb.append("}");
		}

		org.graphper.api.Cluster child = null;
		while (!clusterStack.isEmpty()) {
			currentCluster = clusterStack.pollLast();
			if (child != null) {
				currentCluster.cluster(child);
			}
			child = currentCluster.build();
		}

		graphvizContext.getCurrentBuilder().cluster(currentCluster.build());

		SvekUtils.println(sb);
	}

	private String getSourceInPoint(UmlDiagramType type) {
		if (skinParam.useSwimlanes(type))
			return "sourceIn" + cluster.getColor();

		return null;
	}

	private String getSinkInPoint(UmlDiagramType type) {
		if (skinParam.useSwimlanes(type))
			return "sinkIn" + cluster.getColor();

		return null;
	}

	private String empty() {
		// return "empty" + color;
		// We use the same node with one for thereALinkFromOrToGroup2 as an empty
		// because we cannot put a new node in the nested inside of the cluster
		// if thereALinkFromOrToGroup2 is enabled.
		return Cluster.getSpecialPointId(cluster.getGroup());
	}

	private boolean hasPort() {
		for (EntityPosition pos : entityPositionsExceptNormal())
			if (pos.isPort())
				return true;

		return false;
	}

	private Set<EntityPosition> entityPositionsExceptNormal() {
		final Set<EntityPosition> result = EnumSet.<EntityPosition>noneOf(EntityPosition.class);
		for (SvekNode sh : cluster.getNodes())
			if (sh.getEntityPosition() != EntityPosition.NORMAL)
				result.add(sh.getEntityPosition());

		return Collections.unmodifiableSet(result);
	}

	private void subgraphClusterNoLabel(StringBuilder sb, String id) {
		subgraphClusterWithLabel(sb, id, "\"\"");
	}

	private void subgraphClusterWithLabel(StringBuilder sb, String id, String label) {
		sb.append("subgraph " + cluster.getClusterId() + id + " {");
		sb.append("label=" + label + ";");
	}

	private void printRanks(String rank, List<? extends SvekNode> entries, StringBuilder sb,
			StringBounder stringBounder, GraphvizContext graphvizContext) {
		if (entries.size() > 0) {
			SvekUtils.println(sb);
			for (SvekNode sh2 : entries) {
				NodeBuilder node = Node.builder().fixedSize(true);
				sh2.appendShape(sb, stringBounder, node);
				graphvizContext.getCurrentBuilder().addNode(node.build());
			}
			SvekUtils.println(sb);

			SubgraphBuilder subgraphBuilder = Subgraph.builder();
			sb.append("{rank=" + rank + ";");
			subgraphBuilder.rank(GraphSupportHelper.toRank(rank));
			for (SvekNode sh1 : entries) {
				sb.append(sh1.getUid() + ";");
				Node node = graphvizContext.getIfAbsent(sh1.getUid(), s -> Node.builder().build());
				subgraphBuilder.addNode(node);
			}
			sb.append("}");

			if (hasPort()) {
				boolean arrow = false;
				String node = null;
				for (SvekNode sh : entries) {
					if (arrow)
						sb.append("->");

					arrow = true;
					node = sh.getUid();
					sb.append(node);
				}
				if (arrow)
					sb.append(" [arrowhead=none]");

				sb.append(';');
				SvekUtils.println(sb);
				sb.append(node + "->" + empty() + ";");
				SvekUtils.println(sb);
			}
		}
	}

	private List<SvekNode> withPosition(Set<EntityPosition> positions) {
		final List<SvekNode> result = new ArrayList<>();
		for (final Iterator<SvekNode> it = cluster.getNodes().iterator(); it.hasNext();) {
			final SvekNode sh = it.next();
			if (positions.contains(sh.getEntityPosition()))
				result.add(sh);

		}
		return result;
	}

	private boolean protection0(UmlDiagramType type) {
		if (skinParam.useSwimlanes(type))
			return false;

		return true;
	}

	private boolean protection1(UmlDiagramType type) {
		if (cluster.getGroup().getUSymbol() == USymbols.NODE)
			return true;

		if (skinParam.useSwimlanes(type))
			return false;

		return true;
	}

	private boolean isThereALinkFromOrToGroup(Collection<SvekLine> lines) {
		for (SvekLine line : lines)
			if (line.isLinkFromOrTo(cluster.getGroup()))
				return true;

		return false;
	}

}
