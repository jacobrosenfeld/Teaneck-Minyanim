function initMap() {
  var geocoder = new google.maps.Geocoder();
  geocoder.geocode({'address': address}, function(results, status) {
    if (status === 'OK') {
      var map = new google.maps.Map(document.getElementById('map'), {
        zoom: 15,
        mapId: `ab35d05ad627f5db`,
        center: results[0].geometry.location
      });
        // Add a button for getting directions
  var directionsButton = document.createElement('button');
  directionsButton.innerHTML = 'Get Directions';
  directionsButton.addEventListener('click', function() {
    window.open('https://www.google.com/maps/dir/?api=1&destination=' + marker.getPosition().lat() + ',' + marker.getPosition().lng());
  map.controls[google.maps.ControlPosition.TOP_RIGHT].push(directionsButton);
});
      const shul = [
        {
          position: results[0].geometry.location,
          title: shulname + "/n"+"Next Minyan: "+minyantype+" at "+minyantime,
        },
      ];
      // Create an info window to share between markers.
      const infoWindow = new google.maps.InfoWindow();
    
      // Create the markers.
      shul.forEach(({ position, title }, i) => {
        const pinView = new google.maps.marker.PinView({
          glyphColor: "white",
          scale: 1.5,
        });
        const marker = new google.maps.marker.AdvancedMarkerView({
          position,
          map,
          title: `${title}`,
          content: pinView.element,
        });
    
        // Add a click listener for each marker, and set up the info window.
        marker.addListener("click", ({ domEvent, latLng }) => {
          const { target } = domEvent;
    
          infoWindow.close();
          infoWindow.setContent(marker.title);
          infoWindow.open(marker.map, marker);
        });
      });
      
    } else {
      alert('Geocode was not successful for the following reason: ' + status);
    }
  });
}