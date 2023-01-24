var map;
var marker;

function initMap() {
  var geocoder = new google.maps.Geocoder();
  geocoder.geocode({'address': address}, function(results, status) {
    if (status === 'OK') {
      var map = new google.maps.Map(document.getElementById('map'), {
        zoom: 15,
        mapId: `ab35d05ad627f5db`,
        center: results[0].geometry.location
      });
    };
  });
  
  marker = new google.maps.Marker({
    position: results[0].geometry.location,
    map: map,
    title: shulname
  });
  
  var infoWindow = new google.maps.InfoWindow({
    content: minyantime
  });
  
  marker.addListener('click', function() {
    infoWindow.open(map, marker);
  });
  marker.addListener('mouseover', function() {
    infoWindow.open(map, marker);
  });
  marker.addListener('mouseout', function() {
    infoWindow.close();
  });
}