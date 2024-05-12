package net.bhl.matsim.uam.optimization;

import net.bhl.matsim.uam.analysis.traveltimes.utils.ThreadCounter;
import net.bhl.matsim.uam.optimization.utils.TripItemForOptimization;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.util.LeastCostPathCalculator;
import net.bhl.matsim.uam.optimization.traveltimes.RunCalculateCarTravelTimes;
import net.bhl.matsim.uam.optimization.traveltimes.RunCalculatePTTravelTimes;
import org.matsim.pt.router.TransitRouter;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

public class VertiportCollector implements Runnable {
    /*This class aims to collect all the neighboring vertiports for a given trip to its origin and destination. */
    private static final int processes = Runtime.getRuntime().availableProcessors();
    private static final boolean considerPT=false;
    public VertiportCollector(TripItemForOptimization trip, Network networkCar ,Network networkPt, List<Vertiport> vertiportsCandidates, ThreadCounter threadCounter, ArrayBlockingQueue<LeastCostPathCalculator> carRouters, ArrayBlockingQueue<TransitRouter> ptRouters) {
        this.vertiportsCandidates = vertiportsCandidates;
        this.trip = trip;
        this.carNetwork = networkCar;
        this.ptNetwork = networkPt;
        this.threadCounter=threadCounter;
        this.carRouters=carRouters;
        this.ptRouters=ptRouters;
    }
    public VertiportCollector(TripItemForOptimization trip, Network networkCar , List<Vertiport> vertiportsCandidates) {
        this.vertiportsCandidates = vertiportsCandidates;
        this.trip = trip;
        this.carNetwork = networkCar;
    }
    public VertiportCollector(TripItemForOptimization trip, Network networkCar , List<Vertiport> vertiportsCandidates, LeastCostPathCalculator pathCalculator) {
        this.vertiportsCandidates = vertiportsCandidates;
        this.trip = trip;
        this.carNetwork = networkCar;
        this.pathCalculator=pathCalculator;
    }
    private List<Vertiport> vertiportsCandidates;
    private ArrayBlockingQueue<LeastCostPathCalculator> carRouters;
    private ArrayBlockingQueue<TransitRouter> ptRouters;
    private LeastCostPathCalculator pathCalculator;
    private TripItemForOptimization trip;
    private HashMap<Vertiport,HashMap<String,Double>> accessVertiports= new HashMap<>();
    private HashMap<Vertiport,HashMap<String,Double>> egressVertiports= new HashMap<>();
    private HashMap<Vertiport,HashMap<String,Double>> originNeighborVertiports=new HashMap<>(); // the key is the vertiport, the value is the list (0: travel time, 1: travel distance)
    private HashMap<Vertiport,HashMap<String,Double>> destinationNeighborVertiports=new HashMap<>(); // the key is the vertiport, the value is the list (0: travel time, 1: travel distance)
    private ThreadCounter threadCounter;
    private Network carNetwork;
    private Network ptNetwork;
    List<HashMap<Vertiport,HashMap<String,Double>>> initialMatchingVertiports=new ArrayList<>(); // the first element is the access vertiport, the second element is the egress vertiport

  public double calculateEuciDistance(Coord coord1, Coord coord2) {
      double euciDistance = Math.sqrt(Math.pow(coord1.getX() - coord2.getX(), 2) + Math.pow(coord1.getY() - coord2.getY(), 2));
      return euciDistance;
  }

  public void neighbourVertiportCandidateIdentifier(){
      // iterate all vertiports candidates in the vertiports list
      Iterator<Vertiport> vertiportsIterator = this.vertiportsCandidates.iterator();
      while (vertiportsIterator.hasNext()) {
          Vertiport currentVertiport=vertiportsIterator.next();
        Double accessEuclideanDistance=calculateEuciDistance(this.trip.origin,currentVertiport.coord);
        Double egressEuclideanDistance=calculateEuciDistance(this.trip.destination,currentVertiport.coord);
        if (accessEuclideanDistance<5000){
          this.trip.originNeighborVertiportCandidates.add(currentVertiport);}
        if (egressEuclideanDistance<5000){
          this.trip.destinationNeighborVertiportCandidates.add(currentVertiport);
        }
      }
  }

  public void run() {
      // if both the originNeighborVertiportCandidates and destinationNeighborVertiportCandidates are not null
      if (!(this.trip.originNeighborVertiportCandidates.isEmpty() || this.trip.destinationNeighborVertiportCandidates.isEmpty())) {
          // iterate all vertiports in the originNeighborVertiportCandidates list
          Iterator<Vertiport> originNeighborVertiportCandidatesIterator = this.trip.originNeighborVertiportCandidates.iterator();
          while (originNeighborVertiportCandidatesIterator.hasNext()) {
              Vertiport currentAccessVertiport = originNeighborVertiportCandidatesIterator.next();
              TripItemForOptimization tripItemForOptimizationAccess = new TripItemForOptimization();
              tripItemForOptimizationAccess.origin = this.trip.origin;
              tripItemForOptimizationAccess.destination = currentAccessVertiport.coord;
              tripItemForOptimizationAccess.departureTime = this.trip.departureTime;
              RunCalculateCarTravelTimes.CarTravelTimeCalculator carTravelTimeCalculatorAccess = new RunCalculateCarTravelTimes.CarTravelTimeCalculator(this.threadCounter, carNetwork, tripItemForOptimizationAccess, this.carRouters);
              carTravelTimeCalculatorAccess.run();
              double carAccessTravelTime = tripItemForOptimizationAccess.travelTime;
              double carAccessTravelDistance = tripItemForOptimizationAccess.distance;
              double carAccessTravelGeneralizedCost = 0.428 * carAccessTravelDistance / 1000 + this.trip.VOT * carAccessTravelTime;

              double walkAccessTravelDistance = calculateEuciDistance(tripItemForOptimizationAccess.origin, tripItemForOptimizationAccess.destination) * 1.2;
              double walkAccessTravelTime = walkAccessTravelDistance / 1.1;
              double walkAccessTravelGeneralizedCost = this.trip.VOT * walkAccessTravelTime;
              HashMap<String, Double> accessInformation = new HashMap<>();
              if (considerPT) {
                  RunCalculatePTTravelTimes.PTTravelTimeCalculator ptTravelTimeCalculatorAccess = new RunCalculatePTTravelTimes.PTTravelTimeCalculator(this.threadCounter, carNetwork, tripItemForOptimizationAccess, this.ptRouters);
                  ptTravelTimeCalculatorAccess.run();
                  double ptAccessTravelTime;
                  double ptAccessTravelDistance;
                  double ptAccessTravelGeneralizedCost;


                  if (tripItemForOptimizationAccess.travelTime != 0) {
                      ptAccessTravelTime = tripItemForOptimizationAccess.travelTime;
                      ptAccessTravelDistance = tripItemForOptimizationAccess.distance;
                      ptAccessTravelGeneralizedCost = 0.81 + this.trip.VOT * ptAccessTravelTime;
                  } else {
                      ptAccessTravelTime = 9999;
                      ptAccessTravelDistance = 9999;
                      ptAccessTravelGeneralizedCost = 9999;
                  }

                  // find the mode with the lowest generalized cost
                  if (carAccessTravelGeneralizedCost < ptAccessTravelGeneralizedCost && carAccessTravelGeneralizedCost < walkAccessTravelGeneralizedCost) {
                      this.trip.accessMode = "car";
                      accessInformation.put("travelTime", carAccessTravelTime);
                      accessInformation.put("distance", carAccessTravelDistance);
                      accessInformation.put("generalizedCost", carAccessTravelGeneralizedCost);
                      accessInformation.put("accessMode", 1.0); // 1.0 means car, 0.0 means walk
                  } else if (ptAccessTravelGeneralizedCost < carAccessTravelGeneralizedCost && ptAccessTravelGeneralizedCost < walkAccessTravelGeneralizedCost) {
                      this.trip.accessMode = "pt";
                      accessInformation.put("travelTime", ptAccessTravelTime);
                      accessInformation.put("distance", ptAccessTravelDistance);
                      accessInformation.put("generalizedCost", ptAccessTravelGeneralizedCost);
                      accessInformation.put("accessMode", 2.0); // 1.0 means car, 0.0 means walk
                  } else {
                      this.trip.accessMode = "walk";
                      accessInformation.put("travelTime", walkAccessTravelTime);
                      accessInformation.put("distance", walkAccessTravelDistance);
                      accessInformation.put("generalizedCost", walkAccessTravelGeneralizedCost);
                      accessInformation.put("accessMode", 0.0); // 1.0 means car, 0.0 means walk
                  }
              } else {
                  if (carAccessTravelGeneralizedCost < walkAccessTravelGeneralizedCost) {
                      this.trip.accessMode = "car";
                      accessInformation.put("travelTime", carAccessTravelTime);
                      accessInformation.put("distance", carAccessTravelDistance);
                      accessInformation.put("generalizedCost", carAccessTravelGeneralizedCost);
                      accessInformation.put("accessMode", 1.0); // 1.0 means car, 0.0 means walk
                  } else {
                      this.trip.accessMode = "walk";
                      accessInformation.put("travelTime", walkAccessTravelTime);
                      accessInformation.put("distance", walkAccessTravelDistance);
                      accessInformation.put("generalizedCost", walkAccessTravelGeneralizedCost);
                      accessInformation.put("accessMode", 0.0); // 1.0 means car, 0.0 means walk
                  }
              }


              this.trip.originNeighborVertiportCandidatesTimeAndDistance.put(currentAccessVertiport, accessInformation);
          }
          // iterate all vertiports in the destinationNeighborVertiportCandidates list
          Iterator<Vertiport> destinationNeighborVertiportCandidatesIterator = this.trip.destinationNeighborVertiportCandidates.iterator();
          while (destinationNeighborVertiportCandidatesIterator.hasNext()) {
              Vertiport currentEgressVertiport = destinationNeighborVertiportCandidatesIterator.next();
              TripItemForOptimization tripItemForOptimizationEgress = new TripItemForOptimization();
              tripItemForOptimizationEgress.origin = currentEgressVertiport.coord;
              tripItemForOptimizationEgress.destination = this.trip.destination;
              tripItemForOptimizationEgress.departureTime = this.trip.departureTime + 20 * 60;
              RunCalculateCarTravelTimes.CarTravelTimeCalculator carTravelTimeCalculatorEgress = new RunCalculateCarTravelTimes.CarTravelTimeCalculator(this.threadCounter, carNetwork, tripItemForOptimizationEgress, this.carRouters);
              carTravelTimeCalculatorEgress.run();
              double carEgressTravelTime = tripItemForOptimizationEgress.travelTime;
              double carEgressTravelDistance = tripItemForOptimizationEgress.distance;
              double carEgressTravelGeneralizedCost = 0.428 * carEgressTravelDistance / 1000 + this.trip.VOT * carEgressTravelTime;
              double walkEgressTravelDistance = calculateEuciDistance(tripItemForOptimizationEgress.origin, tripItemForOptimizationEgress.destination) * 1.2;
              double walkEgressTravelTime = walkEgressTravelDistance / 1.1;
              double walkEgressTravelGeneralizedCost = this.trip.VOT * walkEgressTravelTime;
              HashMap<String, Double> egressInformation = new HashMap<>();
              if (considerPT) {
                  RunCalculatePTTravelTimes.PTTravelTimeCalculator ptTravelTimeCalculatorEgress = new RunCalculatePTTravelTimes.PTTravelTimeCalculator(this.threadCounter, carNetwork, tripItemForOptimizationEgress, this.ptRouters);
                  ptTravelTimeCalculatorEgress.run();
                  double ptEgressTravelTime;
                  double ptEgressTravelDistance;
                  double ptEgressTravelGeneralizedCost;
                  if (tripItemForOptimizationEgress.travelTime != 0) {
                      ptEgressTravelTime = tripItemForOptimizationEgress.travelTime;
                      ptEgressTravelDistance = tripItemForOptimizationEgress.distance;
                      ptEgressTravelGeneralizedCost = 0.81 + this.trip.VOT * ptEgressTravelTime;
                  } else {
                      ptEgressTravelTime = 9999;
                      ptEgressTravelDistance = 9999;
                      ptEgressTravelGeneralizedCost = 9999;
                  }
                  // find the mode with the lowest generalized cost
                  if (carEgressTravelGeneralizedCost < ptEgressTravelGeneralizedCost && carEgressTravelGeneralizedCost < walkEgressTravelGeneralizedCost) {
                      this.trip.egressMode = "car";
                      egressInformation.put("travelTime", carEgressTravelTime);
                      egressInformation.put("distance", carEgressTravelDistance);
                      egressInformation.put("generalizedCost", carEgressTravelGeneralizedCost);
                      egressInformation.put("egressMode", 1.0); // 1.0 means car, 0.0 means walk
                  } else if (ptEgressTravelGeneralizedCost < carEgressTravelGeneralizedCost && ptEgressTravelGeneralizedCost < walkEgressTravelGeneralizedCost) {
                      this.trip.egressMode = "pt";
                      egressInformation.put("travelTime", ptEgressTravelTime);
                      egressInformation.put("distance", ptEgressTravelDistance);
                      egressInformation.put("generalizedCost", ptEgressTravelGeneralizedCost);
                      egressInformation.put("egressMode", 2.0); // 1.0 means car, 0.0 means walk
                  } else {
                      this.trip.egressMode = "walk";
                      egressInformation.put("travelTime", walkEgressTravelTime);
                      egressInformation.put("distance", walkEgressTravelDistance);
                      egressInformation.put("generalizedCost", walkEgressTravelGeneralizedCost);
                      egressInformation.put("egressMode", 0.0); // 1.0 means car, 0.0 means walk
                  }
              }
              else {
                  if (carEgressTravelGeneralizedCost < walkEgressTravelGeneralizedCost) {
                      this.trip.egressMode = "car";
                      egressInformation.put("travelTime", carEgressTravelTime);
                      egressInformation.put("distance", carEgressTravelDistance);
                      egressInformation.put("generalizedCost", carEgressTravelGeneralizedCost);
                      egressInformation.put("egressMode", 1.0); // 1.0 means car, 0.0 means walk
                  } else {
                      this.trip.egressMode = "walk";
                      egressInformation.put("travelTime", walkEgressTravelTime);
                      egressInformation.put("distance", walkEgressTravelDistance);
                      egressInformation.put("generalizedCost", walkEgressTravelGeneralizedCost);
                      egressInformation.put("egressMode", 0.0); // 1.0 means car, 0.0 means walk
                  }
              }
              this.trip.destinationNeighborVertiportCandidatesTimeAndDistance.put(currentEgressVertiport, egressInformation);
          }

      }
  }

    public void neighbourVertiportCandidateTimeAndDistanceCalculatorForCompare(){
        // if both the originNeighborVertiportCandidates and destinationNeighborVertiportCandidates are not null
        if (!(this.trip.originNeighborVertiportCandidates.isEmpty() || this.trip.destinationNeighborVertiportCandidates.isEmpty())){
            // iterate all vertiports in the originNeighborVertiportCandidates list
            Iterator<Vertiport> originNeighborVertiportCandidatesIterator = this.trip.originNeighborVertiportCandidates.iterator();
            while (originNeighborVertiportCandidatesIterator.hasNext()) {
                Vertiport currentAccessVertiport=originNeighborVertiportCandidatesIterator.next();
                TripItemForOptimization tripItemForOptimizationAccess =new TripItemForOptimization();
                tripItemForOptimizationAccess.origin=this.trip.origin;
                tripItemForOptimizationAccess.destination=currentAccessVertiport.coord;
                tripItemForOptimizationAccess.departureTime=this.trip.departureTime;
                CompareWithOtherSelection.CarTravelTimeCalculator carTravelTimeCalculatorAccess = new CompareWithOtherSelection.CarTravelTimeCalculator(threadCounter, carNetwork, tripItemForOptimizationAccess);
                Double carAccessTravelTime=carTravelTimeCalculatorAccess.calculateTravelInfo().get("travelTime");
                Double carAccessTravelDistance=carTravelTimeCalculatorAccess.calculateTravelInfo().get("distance");
                Double carAccessTravelGeneralizedCost=0.428*carAccessTravelDistance/1000+this.trip.VOT *carAccessTravelTime;
                Double walkAccessTravelDistance=calculateEuciDistance(tripItemForOptimizationAccess.origin, tripItemForOptimizationAccess.destination)*1.2;
                Double walkAccessTravelTime=walkAccessTravelDistance/1.1;
                Double walkAccessTravelGeneralizedCost = this.trip.VOT*walkAccessTravelTime;
                HashMap<String,Double> accessInformation=new HashMap<>();
                if (carAccessTravelGeneralizedCost<walkAccessTravelGeneralizedCost){
                    this.trip.accessMode="car";
                    accessInformation.put("travelTime",carAccessTravelTime);
                    accessInformation.put("distance",carAccessTravelDistance);
                    accessInformation.put("generalizedCost",carAccessTravelGeneralizedCost);
                    accessInformation.put("accssMode",1.0); // 1.0 means car, 0.0 means walk
                }
                else {
                    this.trip.accessMode="walk";
                    accessInformation.put("travelTime",walkAccessTravelTime);
                    accessInformation.put("distance",walkAccessTravelDistance);
                    accessInformation.put("generalizedCost",walkAccessTravelGeneralizedCost);
                    accessInformation.put("accssMode",0.0); // 1.0 means car, 0.0 means walk
                }

                this.trip.originNeighborVertiportCandidatesTimeAndDistance.put(currentAccessVertiport,accessInformation);
            }
            // iterate all vertiports in the destinationNeighborVertiportCandidates list
            Iterator<Vertiport> destinationNeighborVertiportCandidatesIterator = this.trip.destinationNeighborVertiportCandidates.iterator();
            while (destinationNeighborVertiportCandidatesIterator.hasNext()){
                Vertiport currentEgressVertiport=destinationNeighborVertiportCandidatesIterator.next();
                TripItemForOptimization tripItemForOptimizationEgress =new TripItemForOptimization();
                tripItemForOptimizationEgress.origin=currentEgressVertiport.coord;
                tripItemForOptimizationEgress.destination=this.trip.destination;
                tripItemForOptimizationEgress.departureTime=this.trip.departureTime+20*60;
                CompareWithOtherSelection.CarTravelTimeCalculator carTravelTimeCalculatorEgress = new CompareWithOtherSelection.CarTravelTimeCalculator(threadCounter, carNetwork, tripItemForOptimizationEgress);
                Double carEgressTravelTime=carTravelTimeCalculatorEgress.calculateTravelInfo().get("travelTime");
                Double carEgressTravelDistance=carTravelTimeCalculatorEgress.calculateTravelInfo().get("distance");
                Double carEgressTravelGeneralizedCost=0.428*carEgressTravelDistance/1000+this.trip.VOT *carEgressTravelTime;
                Double walkEgressTravelDistance=calculateEuciDistance(tripItemForOptimizationEgress.origin, tripItemForOptimizationEgress.destination)*1.2;
                Double walkEgressTravelTime=walkEgressTravelDistance/1.1;
                Double walkEgressTravelGeneralizedCost = this.trip.VOT *walkEgressTravelTime;
                HashMap<String,Double> egressInformation=new HashMap<>();
                if (carEgressTravelGeneralizedCost<walkEgressTravelGeneralizedCost){
                    this.trip.egressMode="car";
                    egressInformation.put("travelTime",carEgressTravelTime);
                    egressInformation.put("distance",carEgressTravelDistance);
                    egressInformation.put("generalizedCost",carEgressTravelGeneralizedCost);
                    egressInformation.put("egressMode",1.0); // 1.0 means car, 0.0 means walk
                }
                else{
                    this.trip.egressMode="walk";
                    egressInformation.put("travelTime",walkEgressTravelTime);
                    egressInformation.put("distance",walkEgressTravelDistance);
                    egressInformation.put("generalizedCost",walkEgressTravelGeneralizedCost);
                    egressInformation.put("egressMode",0.0); // 1.0 means car, 0.0 means walk
                }
                this.trip.destinationNeighborVertiportCandidatesTimeAndDistance.put(currentEgressVertiport,egressInformation);
            }
        }
    }

    public void vertiportMatchingInitialization (){
        // find the element with lowest value in the originNeighborVertiports Map
        Map.Entry<Vertiport,HashMap<String,Double>> minOriginNeighborVertiport = null;
        Map.Entry<Vertiport,HashMap<String,Double>> minDestinationNeighborVertiport = null;
        HashMap<Vertiport,HashMap<String,Double>> initialOriginMatchingVertiports=new HashMap<>();
        HashMap<Vertiport,HashMap<String,Double>> initialDestinationMatchingVertiports=new HashMap<>();
       if(this.originNeighborVertiports.isEmpty()||this.destinationNeighborVertiports.isEmpty()){
           System.out.println("The originNeighborVertiports or destinationNeighborVertiports is empty");
     }
       else {
           for (Map.Entry<Vertiport, HashMap<String,Double>> entry : this.originNeighborVertiports.entrySet()) {
               if (minOriginNeighborVertiport == null || minOriginNeighborVertiport.getValue().get("travelTime")> entry.getValue().get("travelTime")) {
                   minOriginNeighborVertiport = entry;
               }
           }
           initialOriginMatchingVertiports.put(minOriginNeighborVertiport.getKey(),minOriginNeighborVertiport.getValue());
           for (Map.Entry<Vertiport, HashMap<String,Double>> entry : this.destinationNeighborVertiports.entrySet()) {
               if (minDestinationNeighborVertiport == null || minDestinationNeighborVertiport.getValue().get("travelTime") > entry.getValue().get("travelTime")) {
                   minDestinationNeighborVertiport = entry;
               }
           }
              initialDestinationMatchingVertiports.put(minDestinationNeighborVertiport.getKey(),minDestinationNeighborVertiport.getValue());
       }
        this.initialMatchingVertiports.add(initialOriginMatchingVertiports);
        this.initialMatchingVertiports.add(initialDestinationMatchingVertiports);

    }

}
