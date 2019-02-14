package com.github.technus.tectech.thing.metaTileEntity.single;

import com.github.technus.tectech.Util;
import com.github.technus.tectech.thing.metaTileEntity.multi.GT_MetaTileEntity_TM_teslaCoil;
import eu.usrv.yamcore.auxiliary.PlayerChatHelper;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.GT_MetaTileEntity_BasicBatteryBuffer;
import gregtech.api.util.GT_ModHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.*;

import static java.lang.Math.round;


public class GT_MetaTileEntity_TeslaCoil extends GT_MetaTileEntity_BasicBatteryBuffer {
    public boolean powerPassToggle = false; //Power Pass for public viewing

    private int scanTime = 0; //Sets scan time to Z E R O :epic:
    private int scanTimeMin = 100; //Min scan time in ticks
    private int scanTimeTill = scanTimeMin; //Set default scan time

    private ArrayList<GT_MetaTileEntity_TM_teslaCoil> eTeslaTowerList = new ArrayList<>(); //Makes a list for BIGG Teslas
    private Map<IGregTechTileEntity, Integer> eTeslaTowerMap = new HashMap<IGregTechTileEntity, Integer>();

    private int histSteps = 20; //Hysteresis Resolution
    private int histSettingLow = 3;
    private int histSettingHigh = 15;
    private int histLowLimit = 1; //How low can you configure it?
    private int histHighLimit = histSteps-1; //How high can you configure it?

    private float histLow = (float)histSettingLow/histSteps; //Power pass is disabled if power is under this fraction
    private float histHigh = (float)histSettingHigh/histSteps; //Power pass is enabled if power is over this fraction

    private int scanRadiusTower = 64; //Radius for tower to tower transfers

    private long outputVoltage = 512; //Tesla Voltage Output
    private long outputCurrent = 1; //Tesla Current Output
    private long outputEuT = outputVoltage * outputCurrent; //Tesla Power Output

    static <K,V extends Comparable<? super V>> SortedSet<Map.Entry<K,V>> entriesSortedByValues(Map<K,V> map) {
        SortedSet<Map.Entry<K,V>> sortedEntries = new TreeSet<Map.Entry<K,V>>(
                new Comparator<Map.Entry<K,V>>() {
                    @Override public int compare(Map.Entry<K,V> e1, Map.Entry<K,V> e2) {
                        int res = e1.getValue().compareTo(e2.getValue());
                        return res != 0 ? res : 1; // Special fix to preserve items with equal values
                    }
                }
        );
        sortedEntries.addAll(map.entrySet());
        return sortedEntries;
    }


    public GT_MetaTileEntity_TeslaCoil(int aID, String aName, String aNameRegional, int aTier, int aSlotCount) {
        super(aID, aName, aNameRegional, aTier, "Tesla Coil Transceiver", aSlotCount);
        Util.setTier(aTier, this);
    }

    public GT_MetaTileEntity_TeslaCoil(String aName, int aTier, String aDescription, ITexture[][][] aTextures, int aSlotCount) {
        super(aName, aTier, aDescription, aTextures, aSlotCount);
    }

    public void onScrewdriverRightClick(byte aSide, EntityPlayer aPlayer, float aX, float aY, float aZ) {
        if (aPlayer.isSneaking()) {
            if (histSettingHigh<histHighLimit) {
                histSettingHigh++;
            } else {
                histSettingHigh=histSettingLow+1;
            }
            histHigh = (float)histSettingHigh/histSteps;
            PlayerChatHelper.SendInfo(aPlayer, "Hysteresis High Changed to " + round(histHigh * 100F) + "%");
        } else {
            if (histSettingLow>histLowLimit) {
                histSettingLow--;
            } else {
                histSettingLow=histSettingHigh-1;
            }
            histLow = (float)histSettingLow/histSteps;
            PlayerChatHelper.SendInfo(aPlayer, "Hysteresis Low Changed to " + round(histLow * 100F) + "%");
        }
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new GT_MetaTileEntity_TeslaCoil(mName, mTier, mDescription, mTextures, mInventory.length);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (aBaseMetaTileEntity.isServerSide()) {
            IGregTechTileEntity mte = getBaseMetaTileEntity();
            this.mCharge = aBaseMetaTileEntity.getStoredEU() / 2L > aBaseMetaTileEntity.getEUCapacity() / 3L;
            this.mDecharge = aBaseMetaTileEntity.getStoredEU() < aBaseMetaTileEntity.getEUCapacity() / 3L;
            this.mBatteryCount = 0;
            this.mChargeableCount = 0;
            ItemStack[] var4 = this.mInventory;
            int var5 = var4.length;

            for (int var6 = 0; var6 < var5; ++var6) {
                ItemStack tStack = var4[var6];
                if (GT_ModHandler.isElectricItem(tStack, this.mTier)) {
                    if (GT_ModHandler.isChargerItem(tStack)) {
                        ++this.mBatteryCount;
                    }
                    ++this.mChargeableCount;
                }
            }

            ////Hysteresis based ePowerPass Config
            long energyMax = getStoredEnergy()[1];
            long energyStored = getStoredEnergy()[0];

            float energyFrac = (float)energyStored/energyMax;
            System.err.println(energyFrac);

            //ePowerPass hist toggle
            if (!powerPassToggle && energyFrac > histHigh) {
                powerPassToggle = true;
            } else if (powerPassToggle && energyFrac < histLow) {
                powerPassToggle = false;
            }

            ////Scanning for active teslas
            scanTime++;
            if (scanTime >= scanTimeTill) {
                scanTime = 0;

                scanRadiusTower = 64; //TODO Generate depending on power stored
                eTeslaTowerList.clear();
                eTeslaTowerMap.clear();

                for (int xPosOffset = -scanRadiusTower; xPosOffset <= scanRadiusTower; xPosOffset++) {
                    for (int yPosOffset = -scanRadiusTower; yPosOffset <= scanRadiusTower; yPosOffset++) {
                        for (int zPosOffset = -scanRadiusTower; zPosOffset <= scanRadiusTower; zPosOffset++) {
                            if (xPosOffset == 0 && yPosOffset == 0 && zPosOffset == 0){
                                continue;
                            }
                            IGregTechTileEntity node = mte.getIGregTechTileEntityOffset(xPosOffset, yPosOffset, zPosOffset);
                            if (node == null) {
                                continue;
                            }
                            IMetaTileEntity nodeInside = node.getMetaTileEntity();
                            if (nodeInside instanceof GT_MetaTileEntity_TM_teslaCoil && node.isActive()){
                                eTeslaTowerList.add((GT_MetaTileEntity_TM_teslaCoil) nodeInside);
                                int distance = (int)round(Math.abs(Math.sqrt(xPosOffset^2 + yPosOffset^2 + zPosOffset^2)));
                                eTeslaTowerMap.put(node,distance);
                            }
                        }
                    }
                }
            }

            for (Map.Entry<IGregTechTileEntity, Integer> Rx : entriesSortedByValues(eTeslaTowerMap)) {
                System.out.println("yote @ : " + Rx.getValue());
            }

            for (IGregTechTileEntity RxRee : eTeslaTowerMap.keySet()) {
                GT_MetaTileEntity_TM_teslaCoil Rx = (GT_MetaTileEntity_TM_teslaCoil) RxRee.getMetaTileEntity();
                if (!Rx.powerPassToggle) {
                    long euTran = outputVoltage;
                    if (Rx.getEUVar() + euTran <= (Rx.maxEUStore() / 2)) {
                        setEUVar(getEUVar() - euTran);
                        Rx.getBaseMetaTileEntity().increaseStoredEnergyUnits(euTran, true);
                        System.err.println("Energy Sent!");
                    }
                }
            }
            //Stuff to do if ePowerPass
            if (powerPassToggle) {
                outputVoltage = 512;//TODO Set Depending On Tier
                outputCurrent = 1;//TODO Generate depending on count of batteries

                outputEuT = outputVoltage * outputCurrent;

                long requestedSumEU = 0;

                //Clean the large tesla list
                for (GT_MetaTileEntity_TM_teslaCoil Rx : eTeslaTowerList.toArray(new GT_MetaTileEntity_TM_teslaCoil[eTeslaTowerList.size()])) {
                    try {
                        requestedSumEU += Rx.maxEUStore() - Rx.getEUVar();
                    } catch (Exception e) {
                        eTeslaTowerList.remove(Rx);
                    }
                }

                //Try to send EU to big teslas
                for (GT_MetaTileEntity_TM_teslaCoil Rx : eTeslaTowerList) {
                    if (!Rx.powerPassToggle) {
                        long euTran = outputVoltage;
                        if (Rx.getEUVar() + euTran <= (Rx.maxEUStore()/2)) {
                            setEUVar(getEUVar() - euTran);
                            Rx.getBaseMetaTileEntity().increaseStoredEnergyUnits(euTran, true);
                            System.err.println("Energy Sent!");
                        }
                    }
                }
            }
        }
    }
}
