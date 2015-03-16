package org.matsim.contrib.josm;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.josm.scenario.EditableScenario;
import org.matsim.contrib.josm.scenario.EditableScenarioUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkReaderMatsimV1;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.data.osm.Way;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IOTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void init() {
	new JOSMFixture(folder.getRoot().getPath()).init(false);
    }

    @Test
    public void readNetworkWithoutTransit() {
	URL url = getClass().getResource("/test-input/pt-tutorial/multimodalnetwork.xml");
	Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
	new NetworkReaderMatsimV1(scenario).parse(url);
	Importer importer = new Importer(url.getFile(), null, Main.getProjection());
	importer.run();
	MATSimLayer layer = importer.getLayer();
	deleteAndUndeleteLinksOneByOne(scenario, layer);
	deleteAndUndeleteLinks(scenario, layer);
	deleteAndUndeleteEverything(scenario, layer);
    }

    private void deleteAndUndeleteLinksOneByOne(Scenario scenario, MATSimLayer layer) {
	int nLinks = scenario.getNetwork().getLinks().size();
	List<Command> commands = new ArrayList<>();
	for (Way way : layer.data.getWays()) {
	    Command delete = DeleteCommand.delete(layer, Arrays.asList(way), false, true);
	    delete.executeCommand();
	    commands.add(delete);
	    nLinks--;
	    Assert.assertEquals(nLinks, layer.getScenario().getNetwork().getLinks().size());
	}
	Assert.assertEquals(0, nLinks);
	for (Command command : commands) {
	    command.undoCommand();
	    nLinks++;
	    Assert.assertEquals(nLinks, layer.getScenario().getNetwork().getLinks().size());
	}
    }

    private void deleteAndUndeleteLinks(Scenario scenario, MATSimLayer layer) {
	Assert.assertEquals(scenario.getNetwork().getNodes().size(), layer.getScenario().getNetwork().getNodes().size());
	Assert.assertEquals(scenario.getNetwork().getLinks().size(), layer.getScenario().getNetwork().getLinks().size());
	Command delete = DeleteCommand.delete(layer, layer.data.getWays(), false, true);
	delete.executeCommand();
	Assert.assertEquals(0, layer.getScenario().getNetwork().getNodes().size());
	Assert.assertEquals(0, layer.getScenario().getNetwork().getLinks().size());
	delete.undoCommand();
	Assert.assertEquals(scenario.getNetwork().getNodes().size(), layer.getScenario().getNetwork().getNodes().size());
	Assert.assertEquals(scenario.getNetwork().getLinks().size(), layer.getScenario().getNetwork().getLinks().size());
    }

    private void deleteAndUndeleteEverything(Scenario scenario, MATSimLayer layer) {
	Assert.assertEquals(scenario.getNetwork().getNodes().size(), layer.getScenario().getNetwork().getNodes().size());
	Assert.assertEquals(scenario.getNetwork().getLinks().size(), layer.getScenario().getNetwork().getLinks().size());
	Command delete = DeleteCommand.delete(layer, layer.data.allPrimitives(), true, true);
	delete.executeCommand();
	Assert.assertEquals(0, layer.getScenario().getNetwork().getNodes().size());
	Assert.assertEquals(0, layer.getScenario().getNetwork().getLinks().size());
	if (scenario.getConfig().scenario().isUseTransit()) {
	    Assert.assertEquals(0, TransitScheduleExporter.convertIdsAndFilterDeleted(layer.getScenario()).getTransitSchedule().getFacilities().size());
	    Assert.assertEquals(0, TransitScheduleExporter.convertIdsAndFilterDeleted(layer.getScenario()).getTransitSchedule().getTransitLines().size());
	    Assert.assertEquals(0, countRoutes(TransitScheduleExporter.convertIdsAndFilterDeleted(layer.getScenario()).getTransitSchedule()));
	}
	delete.undoCommand();
	Assert.assertEquals(scenario.getNetwork().getNodes().size(), layer.getScenario().getNetwork().getNodes().size());
	Assert.assertEquals(scenario.getNetwork().getLinks().size(), layer.getScenario().getNetwork().getLinks().size());
	if (scenario.getConfig().scenario().isUseTransit()) {
	    Assert.assertEquals(scenario.getTransitSchedule().getFacilities().size(), layer.getScenario().getTransitSchedule().getFacilities().size());
	    Assert.assertEquals(scenario.getTransitSchedule().getTransitLines().size(), TransitScheduleExporter.convertIdsAndFilterDeleted(layer.getScenario()).getTransitSchedule().getTransitLines().size());
	    Assert.assertEquals(countRoutes(scenario.getTransitSchedule()), countRoutes(TransitScheduleExporter.convertIdsAndFilterDeleted(layer.getScenario()).getTransitSchedule()));
	    Assert.assertEquals(countLinksInRoutes(scenario.getTransitSchedule()), countLinksInRoutes(TransitScheduleExporter.convertIdsAndFilterDeleted(layer.getScenario()).getTransitSchedule()));
	    Assert.assertEquals(countDepartures(scenario.getTransitSchedule()), countDepartures(TransitScheduleExporter.convertIdsAndFilterDeleted(layer.getScenario()).getTransitSchedule()));
	}
    }

    private int countDepartures(TransitSchedule transitSchedule) {
	int result = 0;
	for (TransitLine transitLine : transitSchedule.getTransitLines().values()) {
	    for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
		result += transitRoute.getDepartures().size();
	    }
	}
	return result;
    }

    private int countLinksInRoutes(TransitSchedule transitSchedule) {
	int result = 0;
	for (TransitLine transitLine : transitSchedule.getTransitLines().values()) {
	    for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
		NetworkRoute route = transitRoute.getRoute();
		if (route != null) {
		    result += route.getLinkIds().size();
		}
	    }
	}
	return result;
    }

    private long countRoutes(TransitSchedule transitSchedule) {
	int result = 0;
	for (TransitLine transitLine : transitSchedule.getTransitLines().values()) {
	    result += transitLine.getRoutes().size();
	}
	return result;
    }

    @Test
    public void readAndWriteNetworkWithTransit() {
	URL networkUrl = getClass().getResource("/test-input/pt-tutorial/multimodalnetwork.xml");
	URL transitScheduleUrl = getClass().getResource("/test-input/pt-tutorial/transitschedule.xml");
	Config config = ConfigUtils.createConfig();
	config.scenario().setUseTransit(true);
	config.scenario().setUseVehicles(true);
	Scenario scenario = ScenarioUtils.createScenario(config);
	new NetworkReaderMatsimV1(scenario).parse(networkUrl);
	new TransitScheduleReader(scenario).readFile(transitScheduleUrl.getFile());
	Importer importer = new Importer(networkUrl.getFile(), transitScheduleUrl.getFile(), Main.getProjection());
	importer.run();
	MATSimLayer layer = importer.getLayer();
	deleteAndUndeleteEverything(scenario, layer);

	Scenario outputScenario = TransitScheduleExporter.convertIdsAndFilterDeleted(importer.getLayer().getScenario());
	checkAttributes(scenario, outputScenario);

    }

    private void checkAttributes(Scenario scenario, Scenario outputScenario) {
	Assert.assertEquals(countDepartures(scenario.getTransitSchedule()), countDepartures(outputScenario.getTransitSchedule()));
	Assert.assertEquals(countLinksInRoutes(scenario.getTransitSchedule()), countLinksInRoutes(outputScenario.getTransitSchedule()));
	Assert.assertEquals(countRoutes(scenario.getTransitSchedule()), countRoutes(outputScenario.getTransitSchedule()));

	for (TransitStopFacility stop : scenario.getTransitSchedule().getFacilities().values()) {
	    compareStops(outputScenario, stop);
	}

	for (TransitLine line : scenario.getTransitSchedule().getTransitLines().values()) {
	    compareLines(outputScenario, line);
	}
    }

    private void compareLines(Scenario outputScenario, TransitLine line) {
	Assert.assertNotNull(outputScenario.getTransitSchedule().getTransitLines().get(line.getId()));
	Assert.assertEquals(line.getRoutes().size(), outputScenario.getTransitSchedule().getTransitLines().get(line.getId()).getRoutes().size());

	for (TransitRoute route : line.getRoutes().values()) {

	    compareRoutes(outputScenario, line, route);
	}
    }

    private void compareRoutes(Scenario outputScenario, TransitLine line, TransitRoute route) {
	Assert.assertNotNull(outputScenario.getTransitSchedule().getTransitLines().get(line.getId()).getRoutes().get(route.getId()));
	Assert.assertEquals(route.getTransportMode(), outputScenario.getTransitSchedule().getTransitLines().get(line.getId()).getRoutes().get(route.getId()).getTransportMode());

	for (Departure departure : route.getDepartures().values()) {
	    compareDepartures(outputScenario, line, route, departure);
	}
    }

    private void compareDepartures(Scenario outputScenario, TransitLine line, TransitRoute route, Departure departure) {
	Assert.assertNotNull(outputScenario.getTransitSchedule().getTransitLines().get(line.getId()).getRoutes().get(route.getId()).getDepartures().get(departure.getId()));
	Assert.assertEquals(departure.getDepartureTime(), outputScenario.getTransitSchedule().getTransitLines().get(line.getId()).getRoutes().get(route.getId()).getDepartures().get(departure.getId()).getDepartureTime(), 0.);
	Assert.assertEquals(departure.getVehicleId().toString(), outputScenario.getTransitSchedule().getTransitLines().get(line.getId()).getRoutes().get(route.getId()).getDepartures().get(departure.getId()).getVehicleId().toString());
    }

    private void compareStops(Scenario outputScenario, TransitStopFacility stop) {
	Assert.assertNotNull(outputScenario.getTransitSchedule().getFacilities().get(stop.getId()));
	Assert.assertEquals(stop.getLinkId().toString(), outputScenario.getTransitSchedule().getFacilities().get(stop.getId()).getLinkId().toString());
    }
}