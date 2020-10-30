import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.jom.OptimizationProblem;
import com.net2plan.interfaces.networkDesign.Demand;
import com.net2plan.interfaces.networkDesign.IAlgorithm;
import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.Net2PlanException;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.Node;
import com.net2plan.utils.Triple;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

public class UnsplittableFlowFormulation implements IAlgorithm
{
	public String executeAlgorithm(NetPlan netPlan, Map<String, String> algorithmParameters, Map<String, String> net2planParameters)
	{
	/* Remove all the carried traffic in the network*/
	netPlan.removeAllUnicastRoutingInformation();
	OptimizationProblem op = new OptimizationProblem ();
	/* add the vector of decision variables */
	final int D = netPlan.getNumberOfDemands();
	final int E = netPlan.getNumberOfLinks ();
	op.addDecisionVariable("x_lkc" , true , new int [] {D , E} , 0 , 1); //x_lkc in {0,1}
	
	double[] v_c= new double[D];
	
	int i=0;
	for (Demand d : netPlan.getDemands())
	{
		v_c[i]=d.getOfferedTraffic();
		System.out.println(v_c[i]);
		i++;
	}
	
	op.setInputParameter("y_c", v_c,"row");
	
	
	// Add the solenoidality constraints
	for (Node n : netPlan.getNodes ())
	{
		op.setInputParameter ("IncomingLinks" , NetPlan.getIndexes (n.getIncomingLinks()) , "row");
		op.setInputParameter ("OutgoingLinks" , NetPlan.getIndexes (n.getOutgoingLinks()) , "row");
		for (Demand d : netPlan.getDemands ())
		{
			op.setInputParameter ("c" , d.getIndex ());
			//op.setInputParameter ("v_c" , d.getOfferedTraffic());
			//nodePairToOfferedTraffic.put(d.getIndex(), d.getOfferedTraffic());
			if (n == d.getIngressNode()) 
				op.addConstraint ("sum(x_lkc(c,OutgoingLinks)) - sum(x_lkc(c,IncomingLinks)) == 1");
			else if (n == d.getEgressNode())
				op.addConstraint ("sum(x_lkc(c,OutgoingLinks)) - sum(x_lkc(c,IncomingLinks)) == -1");
			else
				op.addConstraint ("sum(x_lkc(c,OutgoingLinks)) - sum(x_lkc(c,IncomingLinks)) == 0");
		}
	}

	
	// Add the link capacity constraints
	for (Link lk : netPlan.getLinks ()) {
		op.setInputParameter("lk" , lk.getIndex ());
		op.setInputParameter("linkCapacity" , lk.getCapacity ());
		//op.addConstraint("sum(y_c(0,all)*x_lkc(all, lk)) <= linkCapacity");
		op.addConstraint("sum(y_c*x_lkc(all, lk)) <= linkCapacity");
	}
	
	

	op.setObjectiveFunction("minimize" , "sum (y_c*x_lkc)");
	
	/* call the solver to solve the problem */
	op.solve ("glpk");

	/* An optimal solution was not found */
	if (!op.solutionIsOptimal())
		throw new Net2PlanException ("An optimal solution was not found");


	final DoubleMatrix2D x_lkc = op.getPrimalSolution("x_lkc").view2D();
	//netPlan.setRoutingFromDemandLinkCarriedTraffic(x_lkc , false , false, null);
	Set<Demand> demands = new HashSet<Demand>(netPlan.getDemands());
	netPlan.setRoutingFromDemandLinkCarriedTraffic(x_lkc , true , false, demands, netPlan.getNetworkLayerDefault());
	return "Total carried traffic in the links: " + netPlan.getVectorLinkCarriedTraffic().zSum();
}

	@Override
	public String getDescription()
	{
		return "Flow Formulation Constraints";
	}


	@Override
	public List<Triple<String, String, String>> getParameters()
	{
		final List<Triple<String, String, String>> param = new LinkedList<Triple<String, String, String>> ();
		return param;
	}
}
