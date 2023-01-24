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
      priceTag.title = "Next Minyan: "+minyantype+" at "+minyantime;
      priceTag.textContent = shulname;

      const markerView = new google.maps.marker.AdvancedMarkerView({
        map,
        position: results[0].geometry.location,
        title: title,
        content: textContent
      });
      markerView.addListener("click", ({ domEvent, latLng }) => {
        const { target } = domEvent;
  
        infoWindow.close();
        infoWindow.setContent(markerView.title);
        infoWindow.open(markerView.map, markerView);
      });
      window.initMap = initMap;
    } else {
      alert('Geocode was not successful for the following reason: ' + status);
    }
  });
}