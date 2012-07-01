package net.minecraft.src.universalelectricity.electricity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import net.minecraft.src.universalelectricity.Vector3;
import net.minecraft.src.universalelectricity.extend.TileEntityConductor;

/**
 * This class is used to manage electricity transfering and flow
 * @author Calclavia
 *
 */
public class ElectricityManager
{
	private static List<IElectricUnit> electricUnits = new ArrayList<IElectricUnit>();
	private static List<ElectricityTransferData> electricityTransferQueue = new ArrayList<ElectricityTransferData>();
	private static List<ElectricityConnection> wireConnections = new ArrayList<ElectricityConnection>();
	
	public static int inGameTicks = 0;
	
	/**
	 * Registers an electricity consumer for it to receive electricity.
	 * Call it in your consumer's tile entity constructor like this:
	 * ElectricityManager.registerConsumer(this);
	 * @param newUnit - The consumer to be registered.
	 */
	public static void registerElectricUnit(IElectricUnit newUnit)
	{
		if(!electricUnits.contains(newUnit))
		{
			electricUnits.add(newUnit);
		}
	}
	
	/**
	 * Registers a UE conductor
	 * @param conductor - The conductor tile entity
	 * @return - The ID of the connection line that is assigned to this conductor
	 */
	public static int registerConductor(TileEntityConductor newConductor)
	{
		for(int i = 0; i < wireConnections.size(); i++)
		{
			wireConnections.get(i).cleanUpArray();
			
			if(wireConnections.get(i).conductors.size() == 0)
			{
				wireConnections.remove(i);
			}
		}
		
		int ID = wireConnections.size();
		wireConnections.add(new ElectricityConnection(10, newConductor));
		
		return ID;
	}
	
	public static int mergeConnection(int ID1, int ID2)
	{
		ElectricityConnection connection1 = wireConnections.get(ID1);
		ElectricityConnection connection2 = wireConnections.get(ID2);
		
		connection1.conductors.addAll(connection2.conductors);
		
		wireConnections.set(ID1, connection1);
		
		for(TileEntityConductor conductor : connection2.conductors)
		{
			conductor.connectionID = ID1;
		}
		
		wireConnections.remove(ID2);
		
		System.out.println("Merged");
		
		return ID1;
	}
	
	public static int getConnectionByID(int ID)
	{
		for(int i = 0; i < wireConnections.size(); i++)
		{
			if(wireConnections.get(i).ID == ID)
			{
				return i;
			}
		}
		
		return 0;
	}
	
	/**
	 * Produces electricity into a specific position
	 * @param target - The tile entity in which the electricity is being produced into
	 * @param side - The side in which the electricity is coming in from. 0-5 byte.
	 */
	public static void produceElectricity(TileEntity target, byte side, float watts, float voltage)
	{		
		if(target != null)
		{
			if(target instanceof TileEntityConductor)
			{
				//Find a path between this conductor and all connected units and try to send the electricity to them directly
				TileEntityConductor conductor = (TileEntityConductor)target;
				
				if(wireConnections.get(conductor.connectionID) != null)
				{
					List<IElectricUnit> connectedUnits = wireConnections.get(conductor.connectionID).getConnectedElectricUnits();
					float leftOverWatts = watts;
					
					for(IElectricUnit electricUnit : connectedUnits)
					{
						if(electricUnit.needsElectricity(side) > 0)
						{
				    		float transferWatts = Math.max(0, Math.min(leftOverWatts, Math.min(watts/connectedUnits.size(), electricUnit.needsElectricity(side))));
				    		leftOverWatts -= transferWatts;
							electricityTransferQueue.add(new ElectricityTransferData(electricUnit, side, transferWatts, voltage));
						}
					}
				}
			}
			else if(target instanceof IElectricUnit)
			{
				IElectricUnit electricUnit = (IElectricUnit)target;
				
				if(electricUnit.needsElectricity(side) > 0)
				{
					//Add to the electricity transfer queue to transfer in the update function
					electricityTransferQueue.add(new ElectricityTransferData(electricUnit, side, watts, voltage));
				}
			}
		}
	}
	
	public static void onUpdate()
	{
		for(IElectricUnit electricUnit : electricUnits)
		{
			//Cleanup useless units
			if(electricUnit == null)
			{
				electricUnits.remove(electricUnit);
				break;
			}
			else if(((TileEntity)electricUnit).isInvalid())
			{
				electricUnits.remove(electricUnit);
				break;
			}
			
			if(inGameTicks % electricUnit.getTickInterval() == 0 && electricUnit.getTickInterval() > 0)
			{
				float watts = 0;
				float voltage = 0;
				byte side = -1;
				
				
				//Try to stack all electricity from one side into one update
				for(int i = 0; i < electricityTransferQueue.size(); i ++)
				{
					if(electricityTransferQueue.get(i).eletricUnit == electricUnit)
					{
						//If the side is not set for this tick
						if(side == -1)
						{
							watts = electricityTransferQueue.get(i).watts;
							voltage = electricityTransferQueue.get(i).voltage;
							side = electricityTransferQueue.get(i).side;
							electricityTransferQueue.remove(i);
						}
						else if(electricityTransferQueue.get(i).side == side)
						{
							watts += electricityTransferQueue.get(i).watts;
							voltage = Math.min(voltage, electricityTransferQueue.get(i).voltage);
							electricityTransferQueue.remove(i);
						}
					}
				}
				
				electricUnit.onUpdate(watts, voltage, side);
			}
		}
		
		inGameTicks ++;
	}
}