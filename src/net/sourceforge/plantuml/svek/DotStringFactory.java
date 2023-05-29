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
 *
 *
 */
package net.sourceforge.plantuml.svek;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.plantuml.StringUtils;
import net.sourceforge.plantuml.abel.Entity;
import net.sourceforge.plantuml.abel.EntityFactory;
import net.sourceforge.plantuml.cucadiagram.ICucaDiagram;
import net.sourceforge.plantuml.dot.DotData;
import net.sourceforge.plantuml.dot.DotSplines;
import net.sourceforge.plantuml.dot.Graphviz;
import net.sourceforge.plantuml.dot.GraphvizUtils;
import net.sourceforge.plantuml.dot.GraphvizVersion;
import net.sourceforge.plantuml.dot.GraphvizVersions;
import net.sourceforge.plantuml.dot.ProcessState;
import net.sourceforge.plantuml.klimt.font.StringBounder;
import net.sourceforge.plantuml.klimt.geom.Moveable;
import net.sourceforge.plantuml.klimt.geom.Rankdir;
import net.sourceforge.plantuml.klimt.geom.XPoint2D;
import net.sourceforge.plantuml.security.SFile;
import net.sourceforge.plantuml.skin.UmlDiagramType;
import net.sourceforge.plantuml.style.ISkinParam;
import net.sourceforge.plantuml.utils.Position;
import net.sourceforge.plantuml.vizjs.GraphvizJs;
import net.sourceforge.plantuml.vizjs.GraphvizJsRuntimeException;

import org.graphper.api.GraphContainer.GraphContainerBuilder;
import org.graphper.api.Graphviz.GraphvizBuilder;
import org.graphper.api.LineAttrs;
import org.graphper.api.Node;
import org.graphper.api.attributes.Color;
import org.graphper.api.attributes.Layout;
import org.graphper.api.attributes.Splines;
import org.graphper.def.FlatPoint;
import org.graphper.draw.ClusterDrawProp;
import org.graphper.draw.DrawGraph;
import org.graphper.draw.ExecuteException;
import org.graphper.draw.LineDrawProp;
import org.graphper.draw.NodeDrawProp;
import org.graphper.draw.RenderEngine;
import org.graphper.layout.CombineShifter;
import org.graphper.layout.FlatShifterStrategy;

public class DotStringFactory implements Moveable {

	private final Bibliotekon bibliotekon;

	private final ColorSequence colorSequence;
	private final Cluster root;

	private Cluster current;
	private final UmlDiagramType umlDiagramType;
	private final ISkinParam skinParam;
	private final DotMode dotMode;
	private DotSplines dotSplines;

	private final StringBounder stringBounder;

	public DotStringFactory(StringBounder stringBounder, DotData dotData) {
		this.skinParam = dotData.getSkinParam();
		this.umlDiagramType = dotData.getUmlDiagramType();
		this.dotMode = dotData.getDotMode();

		this.colorSequence = new ColorSequence();
		this.stringBounder = stringBounder;
		this.root = new Cluster(dotData.getEntityFactory().getDiagram(), colorSequence, skinParam,
				dotData.getRootGroup());
		this.current = root;
		this.bibliotekon = new Bibliotekon(dotData.getLinks());
	}

	public DotStringFactory(StringBounder stringBounder, ICucaDiagram diagram) {
		this.skinParam = diagram.getSkinParam();
		this.umlDiagramType = diagram.getUmlDiagramType();
		this.dotMode = DotMode.NORMAL;

		this.colorSequence = new ColorSequence();
		this.stringBounder = stringBounder;
		this.root = new Cluster(diagram, colorSequence, skinParam, diagram.getEntityFactory().getRootGroup());
		this.current = root;
		this.bibliotekon = new Bibliotekon(diagram.getLinks());
	}

	public void addNode(SvekNode node) {
		current.addNode(node);
	}

	private double getHorizontalDzeta() {
		double max = 0;
		for (SvekLine l : bibliotekon.allLines()) {
			final double c = l.getHorizontalDzeta(stringBounder);
			if (c > max)
				max = c;

		}
		return max / 10;
	}

	private double getVerticalDzeta() {
		double max = 0;
		for (SvekLine l : bibliotekon.allLines()) {
			final double c = l.getVerticalDzeta(stringBounder);
			if (c > max)
				max = c;

		}
		if (root.diagram.getPragma().useKermor())
			return max / 100;
		return max / 10;
	}

	// ::comment when __CORE__
	String createDotString(GraphvizBuilder graphBuilder, String... dotStrings) {
		final StringBuilder sb = new StringBuilder();
		GraphvizContext graphvizContext = new GraphvizContext(graphBuilder);

		double nodesep = getHorizontalDzeta();
		if (nodesep < getMinNodeSep())
			nodesep = getMinNodeSep();

		if (skinParam.getNodesep() != 0)
			nodesep = skinParam.getNodesep();

		final String nodesepInches = SvekUtils.pixelToInches(nodesep);
		// Log.println("nodesep=" + nodesepInches);
		double ranksep = getVerticalDzeta();
		if (ranksep < getMinRankSep())
			ranksep = getMinRankSep();

		if (skinParam.getRanksep() != 0)
			ranksep = skinParam.getRanksep();

		final String ranksepInches = SvekUtils.pixelToInches(ranksep);
		// Log.println("ranksep=" + ranksepInches);
		sb.append("digraph unix {");
		SvekUtils.println(sb);

		for (String s : dotStrings) {
			if (s.startsWith("ranksep")) {
				sb.append("ranksep=" + ranksepInches + ";");
				graphBuilder.rankSep(Double.parseDouble(ranksepInches));
			}
			else if (s.startsWith("nodesep")) {
				sb.append("nodesep=" + nodesepInches + ";");
				graphBuilder.nodeSep(Double.parseDouble(nodesepInches));
			}
			else
				sb.append(s);

			SvekUtils.println(sb);
		}
		// sb.append("newrank=true;");
		// SvekUtils.println(sb);
		sb.append("remincross=true;");
		SvekUtils.println(sb);
		sb.append("searchsize=500;");
		graphBuilder.nslimit1(500);
		SvekUtils.println(sb);
		// if (OptionFlags.USE_COMPOUND) {
		// sb.append("compound=true;");
		// SvekUtils.println(sb);
		// }

		dotSplines = skinParam.getDotSplines();
		if (dotSplines == DotSplines.POLYLINE) {
			sb.append("splines=polyline;");
			SvekUtils.println(sb);
			graphBuilder.splines(Splines.POLYLINE);
		} else if (dotSplines == DotSplines.ORTHO) {
			sb.append("splines=ortho;");
			sb.append("forcelabels=true;");
			SvekUtils.println(sb);
			graphBuilder.splines(Splines.ORTHO);
		}

		if (skinParam.getRankdir() == Rankdir.LEFT_TO_RIGHT) {
			sb.append("rankdir=LR;");
			SvekUtils.println(sb);
			graphBuilder.rankdir(org.graphper.api.attributes.Rankdir.LR);
		}

		manageMinMaxCluster(sb);

		if (root.diagram.getPragma().useKermor()) {
			for (SvekLine line : bibliotekon.lines0())
				line.appendLine(getGraphvizVersion(), sb, dotMode, dotSplines, graphvizContext);
			for (SvekLine line : bibliotekon.lines1())
				line.appendLine(getGraphvizVersion(), sb, dotMode, dotSplines, graphvizContext);

			root.printCluster3_forKermor(sb, bibliotekon.allLines(), stringBounder, dotMode, getGraphvizVersion(),
					umlDiagramType, graphvizContext);

		} else {
			root.printCluster1(sb, bibliotekon.allLines(), stringBounder);

			root.printCluster2(sb, bibliotekon.allLines(), stringBounder, dotMode, getGraphvizVersion(),
			                   umlDiagramType, graphvizContext);

			for (SvekLine line : bibliotekon.lines0())
				line.appendLine(getGraphvizVersion(), sb, dotMode, dotSplines, graphvizContext);

			for (SvekLine line : bibliotekon.lines1())
				line.appendLine(getGraphvizVersion(), sb, dotMode, dotSplines, graphvizContext);

		}

		SvekUtils.println(sb);
		sb.append("}");

		return sb.toString();
	}
	// ::done

	private void manageMinMaxCluster(final StringBuilder sb) {
		final List<String> minPointCluster = new ArrayList<>();
		final List<String> maxPointCluster = new ArrayList<>();
		for (Cluster cluster : bibliotekon.allCluster()) {
			final String minPoint = cluster.getMinPoint(umlDiagramType);
			if (minPoint != null)
				minPointCluster.add(minPoint);

			final String maxPoint = cluster.getMaxPoint(umlDiagramType);
			if (maxPoint != null)
				maxPointCluster.add(maxPoint);

		}
		if (minPointCluster.size() > 0) {
			sb.append("{rank=min;");
			for (String s : minPointCluster) {
				sb.append(s);
				sb.append(" [shape=point,width=.01,label=\"\"]");
				sb.append(";");
			}
			sb.append("}");
			SvekUtils.println(sb);
		}
		if (maxPointCluster.size() > 0) {
			sb.append("{rank=max;");
			for (String s : maxPointCluster) {
				sb.append(s);
				sb.append(" [shape=point,width=.01,label=\"\"]");
				sb.append(";");
			}
			sb.append("}");
			SvekUtils.println(sb);
		}
	}

	private int getMinRankSep() {
		if (umlDiagramType == UmlDiagramType.ACTIVITY) {
			// return 29;
			return 40;
		}
		if (root.diagram.getPragma().useKermor())
			return 40;
		return 60;
	}

	private int getMinNodeSep() {
		if (umlDiagramType == UmlDiagramType.ACTIVITY) {
			// return 15;
			return 20;
		}
		return 35;
	}

	// ::uncomment when __CORE__
	// public GraphvizVersion getGraphvizVersion() {
	// return null;
	// }
	// ::done
	// ::comment when __CORE__
	private GraphvizVersion graphvizVersion;

	public GraphvizVersion getGraphvizVersion() {
		if (graphvizVersion == null)
			graphvizVersion = getGraphvizVersionInternal();

		return graphvizVersion;
	}

	private GraphvizVersion getGraphvizVersionInternal() {
		final Graphviz graphviz = GraphvizUtils.create(skinParam, "foo;", "svg");
		if (graphviz instanceof GraphvizJs)
			return GraphvizJs.getGraphvizVersion(false);

		final File f = graphviz.getDotExe();
		return GraphvizVersions.getInstance().getVersion(f);
	}

	public String getSvg(BaseFile basefile, String[] dotOptions, GraphvizBuilder graphvizBuilder) throws IOException {
		String dotString = createDotString(graphvizBuilder, dotOptions);
		System.out.println(dotString);

		if (basefile != null) {
			final SFile f = basefile.getTraceFile("svek.dot");
			SvekUtils.traceString(f, dotString);
		}

		Graphviz graphviz = GraphvizUtils.create(skinParam, dotString, "svg");
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			final ProcessState state = graphviz.createFile3(baos);
			baos.close();
			if (state.differs(ProcessState.TERMINATED_OK()))
				throw new IllegalStateException("Timeout4 " + state, state.getCause());

		} catch (GraphvizJsRuntimeException e) {
			System.err.println("GraphvizJsRuntimeException");
			graphvizVersion = GraphvizJs.getGraphvizVersion(true);
			graphvizBuilder = org.graphper.api.Graphviz.digraph();
			dotString = createDotString(graphvizBuilder, dotOptions);
			graphviz = GraphvizUtils.create(skinParam, dotString, "svg");
			baos = new ByteArrayOutputStream();
			final ProcessState state = graphviz.createFile3(baos);
			baos.close();
			if (state.differs(ProcessState.TERMINATED_OK()))
				throw new IllegalStateException("Timeout4 " + state, state.getCause());

		}
		final byte[] result = baos.toByteArray();
		String s = new String(result, UTF_8);

		if (basefile != null) {
			final SFile f = basefile.getTraceFile("svek.svg");
			SvekUtils.traceString(f, s);
		}

		return s;
	}

	public boolean illegalDotExe() {
		final Graphviz graphviz = GraphvizUtils.create(skinParam, "svg");
		if (graphviz instanceof GraphvizJs)
			return false;

		final File dotExe = graphviz.getDotExe();
		return dotExe == null || dotExe.isFile() == false || dotExe.canRead() == false;
	}

	public File getDotExe() {
		final Graphviz graphviz = GraphvizUtils.create(skinParam, "svg");
		return graphviz.getDotExe();
	}

	public void solve(EntityFactory entityFactory, final String svg) throws IOException, InterruptedException {
		if (svg.length() == 0)
			throw new EmptySvgException();

		Pattern pGraph = Pattern.compile("(?m)\\<svg\\s+width=\"(\\d+)pt\"\\s+height=\"(\\d+)pt\"");
		Matcher mGraph = pGraph.matcher(svg);
		if (mGraph.find() == false) {
			throw new IllegalStateException();
		}

		int fullHeight = Integer.parseInt(mGraph.group(2));

		final Point2DFunction move = new YDelta(fullHeight);
		final SvgResult ss = new SvgResult(svg, new YDelta(0));
		final SvgResult svgResult = new SvgResult(svg, move);
		for (SvekNode node : bibliotekon.allNodes()) {
			int idx = svg.indexOf("<title>" + node.getUid() + "</title>");
			if (node.getType() == ShapeType.RECTANGLE || node.getType() == ShapeType.RECTANGLE_HTML_FOR_PORTS
							|| node.getType() == ShapeType.RECTANGLE_WITH_CIRCLE_INSIDE || node.getType() == ShapeType.FOLDER
							|| node.getType() == ShapeType.DIAMOND || node.getType() == ShapeType.RECTANGLE_PORT) {

				final List<XPoint2D> p = ss.substring(idx).extractList(SvgResult.POINTS_EQUALS);
				final XPoint2D m = SvekUtils.getMinXY(p);
				System.out.println("graphviz before " + node.getUid() + " " + m);

				final List<XPoint2D> points = svgResult.substring(idx).extractList(SvgResult.POINTS_EQUALS);
				final XPoint2D min = SvekUtils.getMinXY(points);
				node.moveSvek(min.getX(), min.getY());
				System.out.println("graphviz after " + node.getUid() + " " + min);
			} else if (node.getType() == ShapeType.ROUND_RECTANGLE) {
				final int idx2 = svg.indexOf("d=\"", idx + 1);
				idx = svg.indexOf("points=\"", idx + 1);
				final List<XPoint2D> points;
				if (idx2 != -1 && (idx == -1 || idx2 < idx)) {
					// GraphViz 2.30
					points = svgResult.substring(idx2).extractList(SvgResult.D_EQUALS);
				} else {
					points = svgResult.substring(idx).extractList(SvgResult.POINTS_EQUALS);
					for (int i = 0; i < 3; i++) {
						idx = svg.indexOf("points=\"", idx + 1);
						points.addAll(svgResult.substring(idx).extractList(SvgResult.POINTS_EQUALS));
					}
				}
				final XPoint2D min = SvekUtils.getMinXY(points);
				node.moveSvek(min.getX(), min.getY());
			} else if (node.getType() == ShapeType.OCTAGON || node.getType() == ShapeType.HEXAGON) {
				idx = svg.indexOf("points=\"", idx + 1);
				final int starting = idx;
				final List<XPoint2D> points = svgResult.substring(starting).extractList(SvgResult.POINTS_EQUALS);
				final XPoint2D min = SvekUtils.getMinXY(points);
				// corner1.manage(minX, minY);
				node.moveSvek(min.getX(), min.getY());
				node.setPolygon(min.getX(), min.getY(), points);
			} else if (node.getType() == ShapeType.CIRCLE || node.getType() == ShapeType.OVAL) {
				final double cx = SvekUtils.getValue(svg, idx, "cx");
				final double cy = SvekUtils.getValue(svg, idx, "cy") + fullHeight;
				final double rx = SvekUtils.getValue(svg, idx, "rx");
				final double ry = SvekUtils.getValue(svg, idx, "ry");
				node.moveSvek(cx - rx, cy - ry);
			} else {
				throw new IllegalStateException(node.getType().toString() + " " + node.getUid());
			}
		}

		for (Cluster cluster : bibliotekon.allCluster()) {
			if (cluster.getGroup().isPacked())
				continue;

			int idx = getClusterIndex(svg, cluster.getColor());
			final int starting = idx;
			final List<XPoint2D> points = svgResult.substring(starting).extractList(SvgResult.POINTS_EQUALS);
			final XPoint2D min = SvekUtils.getMinXY(points);
			final XPoint2D max = SvekUtils.getMaxXY(points);
			cluster.setPosition(min, max);

			if (cluster.getTitleAndAttributeWidth() == 0 || cluster.getTitleAndAttributeHeight() == 0)
				continue;

			idx = getClusterIndex(svg, cluster.getTitleColor());
			final List<XPoint2D> pointsTitle = svgResult.substring(idx).extractList(SvgResult.POINTS_EQUALS);
			cluster.setTitlePosition(SvekUtils.getMinXY(pointsTitle));

			if (root.diagram.getPragma().useKermor()) {
				if (cluster.getGroup().getNotes(Position.TOP).size() > 0) {
					final List<XPoint2D> noteUp = svgResult.substring(getClusterIndex(svg, cluster.getColorNoteTop()))
									.extractList(SvgResult.POINTS_EQUALS);
					cluster.setNoteTopPosition(SvekUtils.getMinXY(noteUp));
				}
				if (cluster.getGroup().getNotes(Position.BOTTOM).size() > 0) {
					final List<XPoint2D> noteBottom = svgResult
									.substring(getClusterIndex(svg, cluster.getColorNoteBottom()))
									.extractList(SvgResult.POINTS_EQUALS);
					cluster.setNoteBottomPosition(SvekUtils.getMinXY(noteBottom));
				}
			}
		}

		for (SvekLine line : bibliotekon.allLines())
			line.solveLine(svgResult, ss);

//		for (SvekLine line : bibliotekon.allLines())
//			line.manageCollision(bibliotekon.allNodes());

	}

	public void solve0(org.graphper.api.Graphviz graphviz) throws IOException, InterruptedException {
		File file = new File("E:\\tmp\\graph1");

		try (OutputStream os = Files.newOutputStream(file.toPath());
						ObjectOutputStream oos = new ObjectOutputStream(os)) {
			oos.writeObject(graphviz);
		}
		DrawGraph drawGraph = Layout.DOT.getLayoutEngine().layout(graphviz);
		CombineShifter shifter = new CombineShifter(new HashSet<>(),
		                                                   Collections.singletonList(new FlatShifterStrategy(-drawGraph.getMinX(), -drawGraph.getMinY())));
		shifter.graph(drawGraph.getGraphvizDrawProp());
		drawGraph.clusters().forEach(shifter::cluster);
		drawGraph.nodes().forEach(shifter::node);
		drawGraph.lines().forEach(shifter::line);
		int fullHeight = (int) drawGraph.getHeight();

		Map<String, NodeDrawProp> nodeDrawPropMap = new HashMap<>();
		for (NodeDrawProp node : drawGraph.nodes()) {
			nodeDrawPropMap.put(node.nodeAttrs().getLabel(), node);
		}

		final Point2DFunction move = new YDelta(fullHeight);
//		final Point2DFunction move = new YDelta(0);
		for (SvekNode node : bibliotekon.allNodes()) {
			String uid = node.getUid();
			NodeDrawProp nodeProp = nodeDrawPropMap.get(uid);
			if (nodeProp == null) {
				continue;
			}
			XPoint2D xPoint2D = new XPoint2D(nodeProp.getLeftBorder(), nodeProp.getUpBorder());
//			System.out.println("graph support before " + node.getUid() + " " + xPoint2D);
			xPoint2D = move.apply(xPoint2D);
//			System.out.println("graph support after " + node.getUid() + " " + xPoint2D);
			node.moveSvek(xPoint2D.getX(), xPoint2D.getY());
		}

		Map<String, ClusterDrawProp> clusterMap = null;
		for (ClusterDrawProp cluster : drawGraph.clusters()) {
			if (clusterMap == null) {
				clusterMap = new HashMap<>();
			}

			clusterMap.put(drawGraph.clusterId(cluster.getCluster()), cluster);
		}

		for (Cluster cluster : bibliotekon.allCluster()) {
			if (clusterMap == null) {
				continue;
			}

			int color = cluster.getColor();
			String id = StringUtils.sharp000000(color);
			ClusterDrawProp clusterDrawProp = clusterMap.get(id);
			if (clusterDrawProp == null) {
				continue;
			}

			XPoint2D min = new XPoint2D(clusterDrawProp.getLeftBorder(), clusterDrawProp.getUpBorder());
			XPoint2D max = new XPoint2D(clusterDrawProp.getRightBorder(), clusterDrawProp.getDownBorder());
			min = move.apply(min);
			max = move.apply(max);
			cluster.setPosition(min, max);

			FlatPoint labelCenter = clusterDrawProp.getLabelCenter();
			if (labelCenter == null) {
				continue;
			}

			cluster.setTitlePosition(move.apply(new XPoint2D(labelCenter.getX(), labelCenter.getY())));
		}

		Map<String, LineDrawProp> lineDrawPropMap = new HashMap<>();
		for (LineDrawProp line : drawGraph.lines()) {
			LineAttrs lineAttrs = line.lineAttrs();
			Color color = lineAttrs.getColor();
			if (color == null) {
				continue;
			}
			lineDrawPropMap.put(color.value(), line);
		}
		for (SvekLine line : bibliotekon.allLines()) {
			line.solveLine0(move, lineDrawPropMap);
//			line.solveLine(svgResult);
		}

//		for (SvekLine line : bibliotekon.allLines()) {
//			line.manageCollision(bibliotekon.allNodes());
//		}

	}

	private int getClusterIndex(final String svg, int colorInt) {
		final String colorString = StringUtils.goLowerCase(StringUtils.sharp000000(colorInt));
		final String keyTitle1 = "=\"" + colorString + "\"";
		int idx = svg.indexOf(keyTitle1);
		if (idx == -1) {
			final String keyTitle2 = "stroke:" + colorString + ";";
			idx = svg.indexOf(keyTitle2);
		}
		if (idx == -1)
			throw new IllegalStateException("Cannot find color " + colorString);

		return idx;
	}
	// ::done

	public void openCluster(Entity g, ClusterHeader clusterHeader) {
		this.current = current.createChild(clusterHeader, colorSequence, skinParam, g);
		bibliotekon.addCluster(this.current);
	}

	public void closeCluster() {
		if (current.getParentCluster() == null)
			throw new IllegalStateException();

		this.current = current.getParentCluster();
	}

	public void moveSvek(double deltaX, double deltaY) {
		for (SvekNode sh : bibliotekon.allNodes())
			sh.moveSvek(deltaX, deltaY);

		for (SvekLine line : bibliotekon.allLines())
			line.moveSvek(deltaX, deltaY);

		for (Cluster cl : bibliotekon.allCluster())
			cl.moveSvek(deltaX, deltaY);

	}

	public final Bibliotekon getBibliotekon() {
		return bibliotekon;
	}

	public ColorSequence getColorSequence() {
		return colorSequence;
	}


	public static class GraphvizContext {
		public GraphvizBuilder graphvizBuilder;

		private Map<String, Node> nodeMap;

		private GraphContainerBuilder currentBuilder;

		public GraphvizContext(GraphvizBuilder graphvizBuilder) {
			this.graphvizBuilder = graphvizBuilder;
		}

		public Node getIfAbsent(String nodeId, Function<String, Node> nodeFunction) {
			if (nodeMap == null) {
				nodeMap = new HashMap<>();
			}
			return nodeMap.computeIfAbsent(nodeId, nodeFunction);
		}

		public GraphContainerBuilder getCurrentBuilder() {
			return currentBuilder != null ? currentBuilder : graphvizBuilder;
		}

		public void setCurrentBuilder(GraphContainerBuilder currentBuilder) {
			this.currentBuilder = currentBuilder;
		}
	}
}
