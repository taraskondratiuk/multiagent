package lab1;

import java.util.*;
import java.util.stream.Collectors;

public class Location {
    static int SPEED_KMH = 60;
    int x;
    int y;
    public Location(int x, int y) {
        this.x = x;
        this.y = y;
    }

    static Location random(int xBound, int yBound) {
        Random r = new java.util.Random();
        return new Location(r.nextInt(xBound), r.nextInt(yBound));
    }
    static double distanceBetweenLocations(Location loc1, Location loc2) {
        return Math.sqrt(Math.pow(loc1.x - loc2.x, 2) + Math.pow(loc1.y - loc2.y, 2));
    }
    static int minutesBetweenLocations(Location loc1, Location loc2) {
        double dist = distanceBetweenLocations(loc1, loc2);
        return (int) (dist / (SPEED_KMH / 60));
    }

    @Override
    public String toString() {
        return "Location{" +
                "x=" + x +
                ", y=" + y +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return x == location.x && y == location.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    static public List<Location> fromString(String s) {
        List<String> coords = Arrays.stream(s.replaceAll("[^\\d ]", "").split(" "))
                .filter(v -> !v.equals("")).collect(Collectors.toList());
        coords.stream().forEach(c -> System.out.println(c + " coord"));
        List<Location> locs = new ArrayList<>();
        for (int i = 0; i < coords.size(); i+=2) {
            locs.add(new Location(Integer.parseInt(coords.get(i)), Integer.parseInt(coords.get(i + 1))));
        }
        return locs;
    }
}
