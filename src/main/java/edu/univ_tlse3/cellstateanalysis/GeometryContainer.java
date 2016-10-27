package edu.univ_tlse3.cellstateanalysis;

import java.util.HashMap;
import java.util.Set;

public class GeometryContainer {
   private HashMap<String, HashMap<Integer, HashMap<String, Object>>> geosOfCells;

   GeometryContainer() {
   }

   /**
    * @param channel channel name
    */
   void addChannel(String channel) {
      if (this.geosOfCells == null) {
         this.geosOfCells = new HashMap<String, HashMap<Integer, HashMap<String, Object>>>();
      }
      if (!this.geosOfCells.containsKey(channel)) {
         this.geosOfCells.put(channel, new HashMap<Integer, HashMap<String, Object>>());
      }
   }

   /**
    * @param channel channel name
    * @param frame   frame name
    */
   private boolean geoFrameExists(String channel, int frame) {
      return getGeosInChannel(channel).containsKey(frame);
   }

   /**
    * @param channel    channel name
    * @param frame      frame name
    * @param geometries geometry of spindle
    */
   void putGeometry(String channel, int frame, HashMap<String, Object> geometries) {
      if (!geoFrameExists(channel, frame)) {
         getGeosInChannel(channel).put(frame, new HashMap<String, Object>());
      }
      getGeosInChannel(channel).put(frame, geometries);
   }

   public Set<String> getUsingChannels() {
      return this.geosOfCells.keySet();
   }

   public HashMap<Integer, HashMap<String, Object>> getGeosInChannel(String channel) {
      return this.geosOfCells.get(channel);
   }

}
