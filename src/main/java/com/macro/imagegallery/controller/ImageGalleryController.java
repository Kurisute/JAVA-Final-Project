package com.macro.imagegallery.controller;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.server.PathParam;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfRect;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.macro.imagegallery.entity.ImageGallery;
import com.macro.imagegallery.service.ImageGalleryService;
import com.macro.imagegallery.processing.ImageProcessor;
import com.macro.imagegallery.processing.FaceDetector;


@Controller
public class ImageGalleryController {
	
	@Value("${uploadDir}")
	private String uploadFolder;

	@Autowired
	private ImageGalleryService imageGalleryService;

	private final Logger log = LoggerFactory.getLogger(this.getClass());

	@GetMapping(value = {"/", "/home"})
	public String addProductPage() {
		return "index";
	}

	@PostMapping("/image/saveImageDetails")
	public @ResponseBody ResponseEntity<?> createProduct(@RequestParam("name") String name,
			@RequestParam("price") double price, @RequestParam("description") String description, Model model, HttpServletRequest request
			,final @RequestParam("image") MultipartFile file) {
		try {
			//String uploadDirectory = System.getProperty("user.dir") + uploadFolder;
			String uploadDirectory = request.getServletContext().getRealPath(uploadFolder);
			log.info("uploadDirectory:: " + uploadDirectory);
			String fileName = file.getOriginalFilename();
			String filePath = Paths.get(uploadDirectory, fileName).toString();
			log.info("FileName: " + file.getOriginalFilename());
			if (fileName == null || fileName.contains("..")) {
				model.addAttribute("invalid", "Sorry! Filename contains invalid path sequence \" + fileName");
				return new ResponseEntity<>("Sorry! Filename contains invalid path sequence " + fileName, HttpStatus.BAD_REQUEST);
			}
			String[] names = name.split(",");
			String[] descriptions = description.split(",");
			Date createDate = new Date();
			log.info("Name: " + names[0]+" "+filePath);
			log.info("description: " + descriptions[0]);
			log.info("price: " + price);
			try {
				File dir = new File(uploadDirectory);
				if (!dir.exists()) {
					log.info("Folder Created");
					dir.mkdirs();
				}
				// Save the file locally
				BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(new File(filePath)));
				stream.write(file.getBytes());
				stream.close();
			} catch (Exception e) {
				log.info("in catch");
				e.printStackTrace();
			}
			byte[] imageData = file.getBytes();
			ImageGallery imageGallery = new ImageGallery();
			imageGallery.setName(names[0]);
			imageGallery.setImage(imageData);
			imageGallery.setPrice(price);
			imageGallery.setDescription(descriptions[0]);
			imageGallery.setCreateDate(createDate);
			imageGalleryService.saveImage(imageGallery);
			log.info("HttpStatus===" + new ResponseEntity<>(HttpStatus.OK));
			return new ResponseEntity<>("Product Saved With File - " + fileName, HttpStatus.OK);
		} catch (Exception e) {
			e.printStackTrace();
			log.info("Exception: " + e);
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
	}
	
	@GetMapping("/image/display/{id}")
	@ResponseBody
	void showImage(@PathVariable("id") Long id, HttpServletResponse response, Optional<ImageGallery> imageGallery)
			throws ServletException, IOException {
		log.info("Id :: " + id);
		imageGallery = imageGalleryService.getImageById(id);
		response.setContentType("image/jpeg, image/jpg, image/png, image/gif");
		response.getOutputStream().write(imageGallery.get().getImage());
		response.getOutputStream().close();
	}

	@DeleteMapping(value = "/image/delete/{id}")
	String deleteImage(@PathParam("id") Long id) {
		imageGalleryService.deleteImageById(id);
		return "redirect:/image/show";
	}
	//@RequestMapping(value="/image/delete", method = RequestMethod.DELETE)
	//@PostMapping("/image/delete")
//	String deleteImage(@RequestParam("id") Long id) {
//		imageGalleryService.deleteImageById(id);
//		return "redirect:/image/show";
//	}

	@GetMapping("/image/delete2")
	String deleteImage2(@RequestParam("id") Long id) {
		imageGalleryService.deleteImageById(id);
		return "redirect:/image/show";
	}



	@GetMapping("/image/imageDetails")
	String showProductDetails(@RequestParam("id") Long id, Optional<ImageGallery> imageGallery, Model model) {
		try {
			log.info("Id :: " + id);
			if (id != 0) {
				imageGallery = imageGalleryService.getImageById(id);
			
				log.info("products :: " + imageGallery);
				if (imageGallery.isPresent()) {
					model.addAttribute("id", imageGallery.get().getId());
					model.addAttribute("description", imageGallery.get().getDescription());
					model.addAttribute("name", imageGallery.get().getName());
					model.addAttribute("price", imageGallery.get().getPrice());
					return "imagedetails";
				}
				return "redirect:/home";
			}
		return "redirect:/home";
		} catch (Exception e) {
			e.printStackTrace();
			return "redirect:/home";
		}
	}

	@GetMapping("/image/show")
	String show(Model map) {
		List<ImageGallery> images = imageGalleryService.getAllActiveImages();
		map.addAttribute("images", images);
		return "images";
	}
	
	@GetMapping("/image/facedetect")
	String faceDetection(@RequestParam("id") Long id, Optional<ImageGallery> imageGallery) {
		// Read and Detect
		log.info("Id :: " + id);
		imageGallery = imageGalleryService.getImageById(id);
		byte[] imagebytes = imageGallery.get().getImage();
		String imgname = imageGallery.get().getName();
		double price = imageGallery.get().getPrice();
		String description = imageGallery.get().getDescription();
		Date createdate = new Date();
		
		Mat imgMat = ImageProcessor.bytes2Mat(imagebytes);
		FaceDetector facedetector = new FaceDetector();
		String envRootDir = System.getProperty("user.dir");
		facedetector.loadCascadeClassifier(envRootDir + "\\detection_model\\haarcascade_frontalface_alt.xml");
		MatOfRect face_region = facedetector.detect(imgMat);
		Mat retimg = facedetector.drawDetection(imgMat, face_region);
		
		// Save image
		MatOfByte mob = new MatOfByte();
		Imgcodecs.imencode(".jpg", retimg, mob);
		byte[] byteArray = mob.toArray();
		
		ImageGallery imageGalleryNew = new ImageGallery();
		imageGalleryNew.setId(id+1);
		imageGalleryNew.setName("detected_" + imgname);
		imageGalleryNew.setPrice(price);
		imageGalleryNew.setDescription(description);
		imageGalleryNew.setCreateDate(createdate);
		imageGalleryNew.setImage(byteArray);
		imageGalleryService.saveImage(imageGalleryNew);
		return "redirect:/image/show";
	}
	
	@GetMapping("/image/gray")
	String gray(@RequestParam("id") Long id, Optional<ImageGallery> imageGallery) {
		log.info("Id :: " + id);
		imageGallery = imageGalleryService.getImageById(id);
		byte[] imagebytes = imageGallery.get().getImage();
		String imgname = imageGallery.get().getName();
		double price = imageGallery.get().getPrice();
		String description = imageGallery.get().getDescription();
		Date createdate = new Date();
		
		Mat imgMat = ImageProcessor.bytes2Mat(imagebytes);
		Mat retimg = ImageProcessor.RGB2Gray(imgMat);
		byte[] byteArray = ImageProcessor.mat2Byte(retimg);
		
		ImageGallery imageGalleryNew = new ImageGallery();
		imageGalleryNew.setId(id+1);
		imageGalleryNew.setName("gray_" + imgname);
		imageGalleryNew.setPrice(price);
		imageGalleryNew.setDescription(description);
		imageGalleryNew.setCreateDate(createdate);
		imageGalleryNew.setImage(byteArray);
		imageGalleryService.saveImage(imageGalleryNew);
		return "redirect:/image/show";
	}
}	

