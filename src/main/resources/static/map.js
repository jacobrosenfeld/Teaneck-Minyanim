function initMap() {
    var geocoder = new google.maps.Geocoder();
    geocoder.geocode({'address': address}, function(results, status) {
      if (status === 'OK') {
        var map = new google.maps.Map(document.getElementById('map'), {
          zoom: 8,
          center: results[0].geometry.location
        });
        var marker = new google.maps.Marker({
          position: results[0].geometry.location,
          map: map,
          title: marker
        });
      } else {
        alert('Geocode was not successful for the following reason: ' + status);
      }
    });
  }