function initMap() {
  var geocoder = new google.maps.Geocoder();
  geocoder.geocode({'address': address}, function(results, status) {
    if (status === 'OK') {
      var map = new google.maps.Map(document.getElementById('map'), {
        zoom: 15,
        mapId: `ab35d05ad627f5db`,
        center: results[0].geometry.location
      });
      const priceTag = document.createElement("div");

      priceTag.className = "price-tag";
      priceTag.textContent = marker;

      const markerView = new google.maps.marker.AdvancedMarkerView({
        map,
        position: results[0].geometry.location,
        content: priceTag,
      });
    } else {
      alert('Geocode was not successful for the following reason: ' + status);
    }
  });
}